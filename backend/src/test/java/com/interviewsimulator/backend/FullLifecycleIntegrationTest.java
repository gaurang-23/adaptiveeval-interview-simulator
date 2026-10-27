package com.interviewsimulator.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.auth.dto.LoginRequest;
import com.interviewsimulator.backend.auth.dto.RegisterRequest;
import com.interviewsimulator.backend.execution.CodeExecutionClient;
import com.interviewsimulator.backend.execution.RunnerExecutionResponse;
import com.interviewsimulator.backend.interview.dto.CreateInterviewRequest;
import com.interviewsimulator.backend.interview.dto.SendMessageRequest;
import com.interviewsimulator.backend.interview.dto.SubmitCodeRequest;
import com.interviewsimulator.backend.interview.dto.SubmitExplanationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FullLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CodeExecutionClient codeExecutionClient;

    @BeforeEach
    void setupRunnerMock() {
        when(codeExecutionClient.executeJavaSubmission(any())).thenAnswer(inv -> new RunnerExecutionResponse(
                "SUCCESS",
                "",
                5,
                5,
                0,
                14L,
                2048L,
                "NONE",
                "",
                "RESTRICTED_LOCAL_SANDBOX",
                List.of(
                        new RunnerExecutionResponse.RunnerTestCaseResult(1L, 0, true, true, "[2,7,11,15]\n9", "[0,1]", "[0,1]", "", "", 5L),
                        new RunnerExecutionResponse.RunnerTestCaseResult(2L, 1, false, true, "[3,3]\n6", "[0,1]", "[0,1]", "", "", 4L)
                )
        ));
    }

    @Test
    void fullInterviewLifecycleAndOwnershipProtectionAgainstMySql() throws Exception {
        String emailA = "candidate." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        RegisterRequest regA = new RegisterRequest(emailA, "Password123!", "Alex Mercer", "Backend Engineer", "Mid-Level");

        MvcResult regRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String tokenA = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("accessToken").asText();

        // Verify login works with the registered credentials
        LoginRequest loginA = new LoginRequest(emailA, "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(emailA));

        // Verify new user's dashboard has hasAssessmentData = false (no fake metrics)
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAssessmentData").value(false))
                .andExpect(jsonPath("$.totalInterviewsCompleted").value(0));

        // Verify public question detail endpoint does NOT leak referenceSolution or hidden test cases
        MvcResult qDetailRes = mockMvc.perform(get("/api/questions/1")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Two Sum with Hash Lookup"))
                .andReturn();
        String qDetailJson = qDetailRes.getResponse().getContentAsString();
        assertFalse(qDetailJson.contains("referenceSolution"), "Public question API must never expose referenceSolution");
        assertFalse(qDetailJson.contains("[-10,7,19,15]"), "Public question API must never expose hidden test cases");

        // Create an interview session
        CreateInterviewRequest createReq = new CreateInterviewRequest(
                "DSA",
                "EASY",
                List.of("Hash Maps", "Stacks"),
                30,
                2,
                "INTERVIEWER_LED",
                null
        );

        MvcResult createRes = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();

        long sessionId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("id").asLong();

        // Start the interview
        mockMvc.perform(post("/api/interviews/" + sessionId + "/start")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.activeQuestion.sequenceNumber").value(1));

        // Request progressive hint level 1 and level 2
        mockMvc.perform(post("/api/interviews/" + sessionId + "/hints")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintLevel").value(1));

        mockMvc.perform(post("/api/interviews/" + sessionId + "/hints")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintLevel").value(2));

        // Submit Java solution
        SubmitCodeRequest submitCode = new SubmitCodeRequest(
                "import java.util.*; public class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0,1}; } }",
                "JAVA",
                false
        );

        MvcResult submitRes = mockMvc.perform(post("/api/interviews/" + sessionId + "/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compilationStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.testsPassed").value(5))
                .andReturn();

        // Verify hidden test case input is masked in submission response
        String submitJson = submitRes.getResponse().getContentAsString();
        assertTrue(submitJson.contains("[Hidden Test Case #2]"), "Hidden test case inputs must be masked in submission response");

        // Submit explanation & complexity analysis
        SubmitExplanationRequest explainReq = new SubmitExplanationRequest(
                "We iterate through the array once, storing each number and its index in a HashMap so we can check if the complement target - nums[i] has already been seen in O(1) time.",
                "O(n)",
                "O(n)",
                "Trades O(n) auxiliary hash table memory to reduce time complexity from O(n^2) brute force to a single O(n) pass."
        );

        mockMvc.perform(post("/api/interviews/" + sessionId + "/explanations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(explainReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeComplexityCorrect").value(true))
                .andExpect(jsonPath("$.spaceComplexityCorrect").value(true));

        // Send a message to the AI interviewer
        SendMessageRequest msgReq = new SendMessageRequest(
                "Are there any other edge cases with duplicate values I should consider?",
                submitCode.code()
        );
        mockMvc.perform(post("/api/interviews/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INTERVIEWER"));

        // Advance to adaptive question #2
        mockMvc.perform(post("/api/interviews/" + sessionId + "/next-question")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeQuestion.sequenceNumber").value(2))
                .andExpect(jsonPath("$.activeQuestion.selectionReason").isNotEmpty());

        // End the interview and verify report generation
        MvcResult reportRes = mockMvc.perform(post("/api/interviews/" + sessionId + "/end")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.overallScore").isNumber())
                .andExpect(jsonPath("$.competencyScores").isArray())
                .andExpect(jsonPath("$.diagnosedMistakes").isArray())
                .andExpect(jsonPath("$.recommendedPractice").isArray())
                .andReturn();

        JsonNode reportNode = objectMapper.readTree(reportRes.getResponse().getContentAsString());
        assertTrue(reportNode.path("overallScore").asDouble() > 0.0);

        // Verify dashboard now reflects persisted assessment data
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAssessmentData").value(true))
                .andExpect(jsonPath("$.totalInterviewsCompleted").value(1));

        // Verify User B cannot access User A's interview session (IDOR protection)
        String emailB = "intruder." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        RegisterRequest regB = new RegisterRequest(emailB, "Password123!", "User B", "SWE", "Junior");
        MvcResult regResB = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regB)))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenB = objectMapper.readTree(regResB.getResponse().getContentAsString()).path("accessToken").asText();

        mockMvc.perform(get("/api/interviews/" + sessionId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/interviews/" + sessionId + "/report")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Verify AI system status and connection test endpoints
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.connectionStatus").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.ai.aiMode").value("FALLBACK_RUBRIC"));

        mockMvc.perform(post("/api/system/ai/test")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.errorCategory").value("MISSING_API_KEY"));

        // Verify user theme preference persistence in MySQL
        mockMvc.perform(patch("/api/users/me/theme")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"light\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themePreference").value("light"));

        // Verify resume screening history endpoint & failed analysis recording when AI_API_KEY is empty
        mockMvc.perform(get("/api/resume-analyses")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
