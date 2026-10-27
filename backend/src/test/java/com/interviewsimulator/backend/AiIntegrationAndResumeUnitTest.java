package com.interviewsimulator.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.ai.*;
import com.interviewsimulator.backend.ai.dto.AiConnectionTestResultDto;
import com.interviewsimulator.backend.ai.dto.ExplanationEvaluationResult;
import com.interviewsimulator.backend.ai.dto.InterviewerReplyResult;
import com.interviewsimulator.backend.common.BadRequestException;
import com.interviewsimulator.backend.common.ForbiddenOperationException;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.resume.*;
import com.interviewsimulator.backend.resume.dto.ResumeAnalysisPageDto;
import com.interviewsimulator.backend.resume.dto.ResumeAnalysisReportDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiIntegrationAndResumeUnitTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void missingApiKeyReportsNotConfiguredAndUsesFallbackMode() {
        AiProviderClient client = new AiProviderClient(
                objectMapper, "gemini", "", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-flash", 5000, null
        );

        assertFalse(client.isConfigured());
        assertEquals(AiConnectionStatus.NOT_CONFIGURED, client.getConnectionStatus());
        assertEquals("FALLBACK_RUBRIC", client.getActiveAiMode());

        AiConnectionTestResultDto testRes = client.testConnection();
        assertEquals(AiConnectionStatus.NOT_CONFIGURED, testRes.connectionStatus());
        assertEquals("MISSING_API_KEY", testRes.errorCategory());
        assertFalse(testRes.configured());
    }

    @Test
    void placeholderApiKeyIsTreatedAsNotConfigured() {
        AiProviderClient client = new AiProviderClient(
                objectMapper, "gemini", "your_api_key_here", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", 5000, null
        );
        assertFalse(client.isConfigured());
        assertEquals(AiConnectionStatus.NOT_CONFIGURED, client.getConnectionStatus());
    }

    @Test
    void configuredCredentialsStartAsUnverifiedAndTransitionToConnectedOnSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-live-key"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        AiProviderClient client = new AiProviderClient(
                objectMapper,
                "gemini",
                "test-live-key",
                "https://generativelanguage.googleapis.com/v1beta/openai/",
                "gemini-2.5-flash",
                5000,
                builder.build()
        );

        assertEquals(AiConnectionStatus.CONFIGURED_UNVERIFIED, client.getConnectionStatus());
        assertFalse(client.isLiveConnected(), "Must not report connected before a real provider call succeeds");

        AiConnectionTestResultDto result = client.testConnection();
        assertEquals(AiConnectionStatus.CONNECTED, result.connectionStatus());
        assertTrue(client.isLiveConnected());
        assertNull(result.errorCategory());
        assertNotNull(result.lastTestedAt());
        server.verify();
    }

    @Test
    void authenticationFailureProducesAuthErrorStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Incorrect API key provided\"}}"));

        AiProviderClient client = new AiProviderClient(
                objectMapper, "openai", "invalid-key", "https://api.openai.com/v1", "gpt-4o-mini", 5000, builder.build()
        );

        AiConnectionTestResultDto res = client.testConnection();
        assertEquals(AiConnectionStatus.AUTH_ERROR, res.connectionStatus());
        assertEquals("AUTHENTICATION_FAILED", res.errorCategory());
        assertEquals("FALLBACK_RUBRIC", client.getActiveAiMode());
        server.verify();
    }

    @Test
    void invalidModelOrEndpointProducesModelOrEndpointErrorStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"The model 'non-existent-model' does not exist\"}}"));

        AiProviderClient client = new AiProviderClient(
                objectMapper, "openai", "valid-key", "https://api.openai.com/v1", "non-existent-model", 5000, builder.build()
        );

        AiConnectionTestResultDto res = client.testConnection();
        assertEquals(AiConnectionStatus.MODEL_OR_ENDPOINT_ERROR, res.connectionStatus());
        assertEquals("INVALID_MODEL_OR_ENDPOINT", res.errorCategory());
        server.verify();
    }

    @Test
    void rateLimitAndQuotaErrorsAreDistinguished() {
        RestClient.Builder rateLimitBuilder = RestClient.builder();
        MockRestServiceServer rateLimitServer = MockRestServiceServer.bindTo(rateLimitBuilder).build();

        rateLimitServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Rate limit reached for requests per minute\"}}"));

        AiProviderClient rateLimitClient = new AiProviderClient(
                objectMapper, "openai", "valid-key", "https://api.openai.com/v1", "gpt-4o-mini", 5000, rateLimitBuilder.build()
        );
        assertEquals(AiConnectionStatus.RATE_LIMITED, rateLimitClient.testConnection().connectionStatus());

        RestClient.Builder quotaBuilder = RestClient.builder();
        MockRestServiceServer quotaServer = MockRestServiceServer.bindTo(quotaBuilder).build();

        quotaServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"You exceeded your current quota, please check your plan and billing details.\"}}"));

        AiProviderClient quotaClient = new AiProviderClient(
                objectMapper, "openai", "valid-key", "https://api.openai.com/v1", "gpt-4o-mini", 5000, quotaBuilder.build()
        );
        assertEquals(AiConnectionStatus.QUOTA_OR_BILLING_ERROR, quotaClient.testConnection().connectionStatus());
    }

    @Test
    void timeoutAndMalformedResponsesAreHandledGracefully() {
        RestClient.Builder timeoutBuilder = RestClient.builder();
        MockRestServiceServer timeoutServer = MockRestServiceServer.bindTo(timeoutBuilder).build();

        timeoutServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
                });

        AiProviderClient timeoutClient = new AiProviderClient(
                objectMapper, "openai", "valid-key", "https://api.openai.com/v1", "gpt-4o-mini", 2000, timeoutBuilder.build()
        );
        AiConnectionTestResultDto timeoutRes = timeoutClient.testConnection();
        assertEquals(AiConnectionStatus.TIMEOUT, timeoutRes.connectionStatus());
        assertEquals("REQUEST_TIMEOUT", timeoutRes.errorCategory());

        RestClient.Builder malformedBuilder = RestClient.builder();
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();

        malformedServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("<html>502 Bad Gateway</html>", MediaType.TEXT_HTML));

        AiProviderClient malformedClient = new AiProviderClient(
                objectMapper, "openai", "valid-key", "https://api.openai.com/v1", "gpt-4o-mini", 2000, malformedBuilder.build()
        );
        AiConnectionTestResultDto malformedRes = malformedClient.testConnection();
        assertEquals(AiConnectionStatus.PROVIDER_ERROR, malformedRes.connectionStatus());
        assertEquals("MALFORMED_PROVIDER_RESPONSE", malformedRes.errorCategory());
    }

    @Test
    void interviewerAndExplanationEvaluatorInvokeLiveProviderAndFallbackWithReasonOnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 1. Live interviewer reply succeeds
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"Consider what invariant your HashMap maintains on each iteration.\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        // 2. Live explanation evaluation succeeds (even when wrapped in markdown ```json fences)
        String fencedEvalJson = """
                ```json
                {
                  "competency": "COMMUNICATION",
                  "score": 88.5,
                  "patternRecognitionScore": 90.0,
                  "complexityAnalysisScore": 85.0,
                  "communicationScore": 90.0,
                  "confidence": 0.91,
                  "evidence": "Candidate clearly articulated single-pass HashMap complement lookup.",
                  "identified_misconceptions": [],
                  "recommended_follow_up": "How would you handle integer overflow if target - nums[i] overflows?"
                }
                ```
                """;
        String openAiEvalEnvelope = "{\"choices\":[{\"message\":{\"content\":" + objectMapper.valueToTree(fencedEvalJson) + "}}]}";

        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(openAiEvalEnvelope, MediaType.APPLICATION_JSON));

        // 3. Subsequent call fails with 401 -> falls back to deterministic rubric with explicit fallbackReason
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid API key\"}}"));

        AiProviderClient client = new AiProviderClient(
                objectMapper, "openai", "live-key", "https://api.openai.com/v1", "gpt-4o-mini", 5000, builder.build()
        );
        AiInterviewerService interviewerService = new AiInterviewerService(client);
        ExplanationEvaluationService evaluationService = new ExplanationEvaluationService(objectMapper, client);

        QuestionEntity q = new QuestionEntity();
        q.setId(1L);
        q.setTitle("Two Sum");
        q.setTopic("Hash Maps");
        q.setDifficulty("EASY");
        q.setExpectedTimeComplexity("O(n)");
        q.setExpectedSpaceComplexity("O(n)");
        q.setExpectedConceptsJson("[\"Hash Map\"]");
        q.setRubricJson("{}");

        InterviewerReplyResult liveChat = interviewerService.respondToCandidateMessage(
                q, "I plan to use a HashMap for complement lookups.", "class Solution {}", 0, null
        );
        assertEquals("LIVE_LLM", liveChat.sourceType());
        assertNull(liveChat.fallbackReason());
        assertTrue(liveChat.replyContent().contains("HashMap"));

        ExplanationEvaluationResult liveEval = evaluationService.evaluateExplanation(
                q, "class Solution {}", "Single pass HashMap lookup.", "O(n)", "O(n)", "O(n) memory for O(1) lookup"
        );
        assertEquals("LIVE_LLM", liveEval.sourceType());
        assertEquals(88.5, liveEval.score());
        assertNull(liveEval.fallbackReason());

        InterviewerReplyResult fallbackChat = interviewerService.respondToCandidateMessage(
                q, "What about complexity?", "class Solution {}", 0, null
        );
        assertEquals("FALLBACK_RUBRIC", fallbackChat.sourceType());
        assertNotNull(fallbackChat.fallbackReason());
        assertTrue(fallbackChat.fallbackReason().contains("AUTHENTICATION_FAILED"));

        server.verify();
    }

    @Test
    void resumeExtractorExtractsValidPdfAndDocxAndRejectsInvalidUploads() throws Exception {
        ResumeTextExtractorService extractor = new ResumeTextExtractorService(1024 * 1024); // 1 MB limit

        // 1. Valid PDF extraction
        byte[] pdfBytes = createSamplePdfBytes("Alex Mercer - Senior Java Backend Engineer. Built Spring Boot microservices and MySQL schemas.");
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "alex_resume.pdf", "application/pdf", pdfBytes
        );
        var extractedPdf = extractor.extractResumeText(validPdf);
        assertEquals("alex_resume.pdf", extractedPdf.filename());
        assertTrue(extractedPdf.extractedText().contains("Spring Boot"));

        // 2. Valid DOCX extraction
        byte[] docxBytes = createSampleDocxBytes("Alex Mercer - Backend Engineer experienced in Java 21, Spring Boot, REST APIs, and MySQL 8.");
        MockMultipartFile validDocx = new MockMultipartFile(
                "file", "alex_resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes
        );
        var extractedDocx = extractor.extractResumeText(validDocx);
        assertEquals("alex_resume.docx", extractedDocx.filename());
        assertTrue(extractedDocx.extractedText().contains("Java 21"));

        // 3. Empty file rejected
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThrows(BadRequestException.class, () -> extractor.extractResumeText(emptyFile));

        // 4. Unsupported extension rejected
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "Some resume text".getBytes(StandardCharsets.UTF_8)
        );
        assertThrows(BadRequestException.class, () -> extractor.extractResumeText(txtFile));

        // 5. Fake PDF (invalid magic bytes) rejected
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "Not a real PDF file header".getBytes(StandardCharsets.UTF_8)
        );
        BadRequestException sigEx = assertThrows(BadRequestException.class, () -> extractor.extractResumeText(fakePdf));
        assertTrue(sigEx.getMessage().toLowerCase().contains("signature"));

        // 6. Blank PDF with no extractable text explains OCR requirement
        byte[] blankPdfBytes = createBlankPdfBytes();
        MockMultipartFile blankPdf = new MockMultipartFile("file", "scanned.pdf", "application/pdf", blankPdfBytes);
        BadRequestException ocrEx = assertThrows(BadRequestException.class, () -> extractor.extractResumeText(blankPdf));
        assertTrue(ocrEx.getMessage().toLowerCase().contains("ocr"));

        // 7. Oversized upload rejected
        ResumeTextExtractorService tinyExtractor = new ResumeTextExtractorService(100);
        assertThrows(BadRequestException.class, () -> tinyExtractor.extractResumeText(validPdf));
    }

    @Test
    void resumeAnalysisServiceEvaluatesPersistsFiltersAndEnforcesOwnership() throws Exception {
        List<ResumeAnalysisEntity> inMemoryDb = new ArrayList<>();
        AtomicLong idGen = new AtomicLong(1);

        ResumeAnalysisRepository repo = mock(ResumeAnalysisRepository.class);
        when(repo.saveAndFlush(any(ResumeAnalysisEntity.class))).thenAnswer(inv -> {
            ResumeAnalysisEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(idGen.getAndIncrement());
                inMemoryDb.add(e);
            } else {
                inMemoryDb.removeIf(existing -> existing.getId().equals(e.getId()));
                inMemoryDb.add(e);
            }
            return e;
        });
        when(repo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return inMemoryDb.stream().filter(e -> e.getId().equals(id)).findFirst();
        });
        when(repo.findByUserIdOrderByCreatedAtDesc(anyLong())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            return inMemoryDb.stream().filter(e -> e.getUserId().equals(uid)).toList();
        });
        doAnswer(inv -> {
            ResumeAnalysisEntity e = inv.getArgument(0);
            inMemoryDb.removeIf(existing -> existing.getId().equals(e.getId()));
            return null;
        }).when(repo).delete(any(ResumeAnalysisEntity.class));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String llmAnalysisJson = """
                {
                  "overallScore": 82,
                  "summary": "Strong alignment on Java, Spring Boot, REST APIs, and MySQL; Kubernetes is not evidenced in the provided resume.",
                  "dimensionScores": [
                    { "dimensionKey": "REQUIRED_TECHNICAL_SKILLS", "label": "Required Technical Skills", "score": 25, "maxScore": 30, "rationale": "Strong Java 21, Spring Boot, and MySQL evidence; missing Kubernetes." },
                    { "dimensionKey": "RELEVANT_EXPERIENCE", "label": "Relevant Experience & Responsibilities", "score": 21, "maxScore": 25, "rationale": "3+ years building backend microservices." },
                    { "dimensionKey": "PROJECTS_AND_ACCOMPLISHMENTS", "label": "Projects & Demonstrated Accomplishments", "score": 17, "maxScore": 20, "rationale": "Demonstrated distributed code runner architecture." },
                    { "dimensionKey": "ROLE_QUALIFICATIONS", "label": "Role-Specific Qualifications & Requirements", "score": 12, "maxScore": 15, "rationale": "Matches mid-to-senior backend scope." },
                    { "dimensionKey": "EVIDENCE_OF_IMPACT", "label": "Evidence of Impact & Measurable Outcomes", "score": 7, "maxScore": 10, "rationale": "Mentions latency reduction; could quantify throughput further." }
                  ],
                  "strengths": [
                    { "title": "Spring Boot & MySQL Architecture", "resumeEvidence": "Built Spring Boot microservices and MySQL schemas.", "relatedRequirement": "Java & Spring Boot Backend" }
                  ],
                  "weaknesses": [
                    { "gap": "Kubernetes Orchestration", "detail": "Not evidenced in the provided resume.", "relatedRequirement": "Kubernetes production deployment", "actionableStep": "Consider adding details if you have relevant experience with container orchestration." }
                  ],
                  "matchedRequirements": [
                    { "requirement": "Java & Spring Boot", "resumeEvidence": "Senior Java Backend Engineer. Built Spring Boot microservices." }
                  ],
                  "partiallyMatchedRequirements": [
                    { "requirement": "Cloud Containerization", "resumeEvidence": "Docker mentioned", "missingDetail": "Kubernetes not evidenced in the provided resume" }
                  ],
                  "missingRequirements": [
                    { "requirement": "Kubernetes", "statusNote": "Not evidenced in the provided resume — consider adding details if you have relevant experience." }
                  ],
                  "technicalSkills": [
                    { "skill": "Spring Boot", "alignmentStatus": "MATCHED", "resumeEvidence": "Built Spring Boot microservices" },
                    { "skill": "Kubernetes", "alignmentStatus": "MISSING", "resumeEvidence": "Not evidenced in the provided resume" }
                  ],
                  "experienceAssessment": {
                    "summary": "Solid backend engineering track record.",
                    "relevantResponsibilities": ["Designed REST APIs", "Managed MySQL schema migrations"],
                    "seniorityAndScopeNote": "Aligned with target role."
                  },
                  "projectAssessment": {
                    "summary": "Relevant backend systems projects.",
                    "keyProjectsAndOutcomes": ["Isolated code runner service"],
                    "measurableImpactNote": "Quantify request volume where possible."
                  },
                  "improvementRecommendations": [
                    { "priority": "HIGH", "targetSection": "Experience", "recommendation": "Add throughput metrics to Spring Boot service bullets.", "rationale": "Strengthens evidence of impact." }
                  ],
                  "suggestedResumeEdits": [
                    { "section": "Experience", "originalContext": "Built Spring Boot microservices and MySQL schemas.", "suggestedTruthfulWording": "Engineered Spring Boot REST microservices backed by normalized MySQL schemas.", "alignmentReason": "Clarifies architecture while preserving original facts." }
                  ],
                  "interviewPreparationTopics": [
                    { "topic": "Spring Transaction Isolation & MySQL Indexing", "reason": "Core backend requirement", "sampleQuestion": "How do composite B-tree indexes affect range queries in MySQL?" }
                  ]
                }
                """;
        String envelope = "{\"choices\":[{\"message\":{\"content\":" + objectMapper.valueToTree(llmAnalysisJson) + "}}]}";

        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        AiProviderClient aiClient = new AiProviderClient(
                objectMapper, "openai", "live-key", "https://api.openai.com/v1", "gpt-4o-mini", 5000, builder.build()
        );
        ResumeTextExtractorService extractor = new ResumeTextExtractorService(5_242_880L);
        ResumeAnalysisService service = new ResumeAnalysisService(
                repo, extractor, aiClient, objectMapper, 30, 25, 20, 15, 10
        );

        byte[] pdfBytes = createSamplePdfBytes("Alex Mercer - Senior Java Backend Engineer. Built Spring Boot microservices and MySQL schemas with Docker.");
        MockMultipartFile resumeFile = new MockMultipartFile("file", "alex_cv.pdf", "application/pdf", pdfBytes);
        String jd = "We are hiring a Senior Backend Engineer with strong Java, Spring Boot, REST APIs, MySQL, and Kubernetes experience.";

        ResumeAnalysisReportDto report = service.analyzeResume(10L, resumeFile, "Senior Backend Engineer", jd);
        assertEquals(82, report.overallScore());
        assertEquals("COMPLETED", report.analysisStatus());
        assertEquals(5, report.dimensionScores().size());
        assertEquals("Not evidenced in the provided resume — consider adding details if you have relevant experience.",
                report.result().missingRequirements().get(0).statusNote());

        // Reopen report without another LLM call (MockRestServiceServer would fail if another call were made!)
        ResumeAnalysisReportDto reopened = service.getOwnedReport(10L, report.id());
        assertEquals(82, reopened.overallScore());
        assertEquals("alex_cv.pdf", reopened.originalFilename());

        // Ownership protection: User 99 cannot view or delete User 10's report
        assertThrows(ForbiddenOperationException.class, () -> service.getOwnedReport(99L, report.id()));
        assertThrows(ForbiddenOperationException.class, () -> service.deleteOwnedAnalysis(99L, report.id()));

        // History search & filtering
        ResumeAnalysisPageDto historyPage = service.listUserHistory(10L, "alex_cv", "COMPLETED", 80, 90, "HIGHEST_SCORE", 0, 10);
        assertEquals(1, historyPage.totalElements());

        // Delete analysis by owner
        service.deleteOwnedAnalysis(10L, report.id());
        assertEquals(0, service.listUserHistory(10L, null, "ALL", null, null, "NEWEST", 0, 10).totalElements());

        server.verify();
    }

    @Test
    void resumeAnalysisRejectsInvalidLlmScoresWithoutFabricatingFallbackScores() throws Exception {
        JsonNode invalidScoreJson = objectMapper.readTree("{\"overallScore\": 150, \"dimensionScores\": []}");
        ResumeTextExtractorService extractor = new ResumeTextExtractorService(5_242_880L);
        AiProviderClient aiClient = new AiProviderClient(
                objectMapper, "openai", "live-key", "https://api.openai.com/v1", "gpt-4o-mini", 5000, null
        );
        ResumeAnalysisService service = new ResumeAnalysisService(
                mock(ResumeAnalysisRepository.class), extractor, aiClient, objectMapper, 30, 25, 20, 15, 10
        );

        AiCallException ex = assertThrows(AiCallException.class, () -> service.validateAndNormalizeResult(invalidScoreJson));
        assertEquals("INVALID_STRUCTURED_SCORES", ex.getErrorCategory());
    }

    private static byte[] createSamplePdfBytes(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] createBlankPdfBytes() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] createSampleDocxBytes(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }
}
