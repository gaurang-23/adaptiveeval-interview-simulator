package com.interviewsimulator.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.ai.dto.AiConnectionTestResultDto;
import com.interviewsimulator.backend.ai.dto.InterviewerReplyResult;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.submission.AttemptEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AiInterviewerService {

    private static final Logger log = LoggerFactory.getLogger(AiInterviewerService.class);

    private final AiProviderClient aiProviderClient;

    @Autowired
    public AiInterviewerService(AiProviderClient aiProviderClient) {
        this.aiProviderClient = aiProviderClient;
    }

    public AiInterviewerService(
            ObjectMapper objectMapper,
            String providerName,
            String apiKey,
            String baseUrl,
            String model,
            int timeoutMs
    ) {
        this(new AiProviderClient(objectMapper, providerName, apiKey, baseUrl, model, timeoutMs));
    }

    public boolean isLiveLlmAvailable() {
        return aiProviderClient.isConfigured();
    }

    public boolean isLiveConnected() {
        return aiProviderClient.isLiveConnected();
    }

    public AiConnectionStatus getConnectionStatus() {
        return aiProviderClient.getConnectionStatus();
    }

    public String getActiveAiMode() {
        return aiProviderClient.getActiveAiMode();
    }

    public Map<String, Object> getAiStatusMetadata() {
        return aiProviderClient.getStatusMetadata();
    }

    public AiConnectionTestResultDto testConnection() {
        return aiProviderClient.testConnection();
    }

    public InterviewerReplyResult generateQuestionIntro(
            QuestionEntity question,
            int sequenceNumber,
            int totalQuestions,
            String selectionReason,
            String interviewStyle
    ) {
        String styleNote = "INTERVIEWER_LED".equalsIgnoreCase(interviewStyle)
                ? "Before jumping straight into coding, feel free to share your high-level approach or invariant in the chat, or start implementing and test against the sample cases."
                : "Take a moment to review the problem statement and constraints, then implement your solution in Java and run the sample test cases.";

        String msg = String.format(
                "**Question %d of %d — %s (%s · %s)**\n\n*Adaptive Selection Context:* %s\n\n%s Once you submit your code, remember to complete the **Algorithm & Complexity Explanation** panel so we can evaluate your complexity analysis and communication.",
                sequenceNumber,
                totalQuestions,
                question.getTitle(),
                question.getTopic(),
                question.getDifficulty(),
                selectionReason,
                styleNote
        );

        return new InterviewerReplyResult(msg, "INTRO", "PROBLEM_INTRODUCTION", "DETERMINISTIC", null);
    }

    public InterviewerReplyResult generatePostSubmissionFollowUp(
            QuestionEntity question,
            AttemptEntity attempt,
            int priorAttemptsCount
    ) {
        String fallbackReason = null;
        if (aiProviderClient.shouldAttemptLiveCall()) {
            try {
                InterviewerReplyResult liveFollowUp = callLivePostSubmissionFollowUp(question, attempt, priorAttemptsCount);
                if (liveFollowUp != null) {
                    return liveFollowUp;
                }
            } catch (AiCallException e) {
                fallbackReason = e.getErrorCategory() + ": " + e.getMessage();
                log.warn("Live LLM post-submission follow-up failed [{}], using deterministic follow-up.", e.getErrorCategory());
            } catch (Exception e) {
                fallbackReason = "PROVIDER_ERROR: " + e.getClass().getSimpleName();
                log.warn("Live LLM post-submission follow-up failed, using deterministic follow-up: {}", e.getMessage());
            }
        } else {
            fallbackReason = "NOT_CONFIGURED: AI_API_KEY is not configured.";
        }

        return generateDeterministicPostSubmissionFollowUp(question, attempt, fallbackReason);
    }

    private InterviewerReplyResult callLivePostSubmissionFollowUp(
            QuestionEntity question,
            AttemptEntity attempt,
            int priorAttemptsCount
    ) {
        String systemPrompt = """
                You are a senior Java technical interviewer providing immediate follow-up after a candidate submits code.
                Rules:
                1. Deterministic test execution is authoritative—do NOT contradict the reported compilation or test pass counts.
                2. NEVER write out the full Java solution.
                3. If tests failed or compilation failed, ask 1-2 targeted debugging or edge-case questions based on their code.
                4. If all tests passed, congratulate briefly and ask a focused follow-up question on time/space complexity or invariants.
                5. Keep response concise (2-4 sentences).
                """;

        String userPrompt = "Problem: " + question.getTitle() + " (" + question.getTopic() + ", " + question.getDifficulty() + ")\n"
                + "Target Complexity: Time " + question.getExpectedTimeComplexity() + ", Space " + question.getExpectedSpaceComplexity() + "\n"
                + "Attempt #" + priorAttemptsCount + " Authoritative Execution Result:\n"
                + "- Compilation: " + attempt.getCompilationStatus() + "\n"
                + "- Tests Passed: " + attempt.getTestsPassed() + " / " + attempt.getTestsTotal() + "\n"
                + "- Failure Category: " + attempt.getFailureCategory() + "\n"
                + "- Runtime: " + attempt.getRuntimeMs() + " ms\n"
                + "Candidate Code:\n" + (attempt.getSubmittedCode() != null ? attempt.getSubmittedCode() : "");

        String content = aiProviderClient.chatCompletion(systemPrompt, userPrompt, 0.3);
        String msgType = (attempt.getTestsPassed() < attempt.getTestsTotal() || !"SUCCESS".equals(attempt.getCompilationStatus()))
                ? "DEBUG_PROMPT"
                : "FOLLOW_UP";
        String stage = (attempt.getTestsPassed() < attempt.getTestsTotal()) ? "DEBUGGING" : "COMPLEXITY_AND_EXPLANATION";
        return new InterviewerReplyResult(content, msgType, stage, "LIVE_LLM", null);
    }

    private InterviewerReplyResult generateDeterministicPostSubmissionFollowUp(
            QuestionEntity question,
            AttemptEntity attempt,
            String fallbackReason
    ) {
        String sourceType = "FALLBACK_RUBRIC";
        if ("COMPILATION_ERROR".equals(attempt.getCompilationStatus())) {
            return new InterviewerReplyResult(
                    "Your submission encountered a **compilation error**. Inspect the compiler diagnostics in the output console—check method signatures, variable types, and return statements—then run the sample tests again.",
                    "DEBUG_PROMPT",
                    "DEBUGGING",
                    sourceType,
                    fallbackReason
            );
        }

        if (attempt.getTestsPassed() < attempt.getTestsTotal()) {
            if ("EDGE_CASE_FAILURE".equals(attempt.getFailureCategory())) {
                return new InterviewerReplyResult(
                        String.format(
                                "Your code passed the sample test cases (%d/%d total passed), but failed on **hidden boundary/edge cases**. Think about extreme inputs: empty or single-element inputs, duplicate values, negative numbers, or boundary indices (`0` and `n - 1`). What assumption in your current code might break on those?",
                                attempt.getTestsPassed(),
                                attempt.getTestsTotal()
                        ),
                        "FOLLOW_UP",
                        "EDGE_CASE_PROBING",
                        sourceType,
                        fallbackReason
                );
            }
            if ("TIME_LIMIT_EXCEEDED".equals(attempt.getFailureCategory())) {
                return new InterviewerReplyResult(
                        String.format(
                                "Your submission exceeded the **time limit** (%d/%d tests passed). Check whether a loop condition fails to advance a pointer or if your current algorithm has higher time complexity than the target `%s`.",
                                attempt.getTestsPassed(),
                                attempt.getTestsTotal(),
                                question.getExpectedTimeComplexity()
                        ),
                        "FOLLOW_UP",
                        "COMPLEXITY_PROBING",
                        sourceType,
                        fallbackReason
                );
            }
            return new InterviewerReplyResult(
                    String.format(
                            "Your submission passed **%d of %d** test cases (Failure category: `%s`). Trace your code step-by-step on the first failing test case—what value does your state hold just before returning?",
                            attempt.getTestsPassed(),
                            attempt.getTestsTotal(),
                            attempt.getFailureCategory()
                    ),
                    "DEBUG_PROMPT",
                    "DEBUGGING",
                    sourceType,
                    fallbackReason
            );
        }

        return new InterviewerReplyResult(
                String.format(
                        "All **%d/%d** test cases (including hidden tests) passed in `%d ms`! Now let's evaluate your reasoning: please submit your **Algorithm & Complexity Explanation** (or explain here in chat) covering:\n1. Why your algorithm's core invariant guarantees correctness.\n2. The exact time and space complexity (`Big-O`) and why.\n3. Key trade-offs or edge cases you handled.",
                        attempt.getTestsPassed(),
                        attempt.getTestsTotal(),
                        attempt.getRuntimeMs()
                ),
                "FOLLOW_UP",
                "COMPLEXITY_AND_EXPLANATION",
                sourceType,
                fallbackReason
        );
    }

    public InterviewerReplyResult respondToCandidateMessage(
            QuestionEntity question,
            String candidateMessage,
            String currentCode,
            int hintsUsed,
            AttemptEntity latestAttempt
    ) {
        String fallbackReason = null;
        if (aiProviderClient.shouldAttemptLiveCall()) {
            try {
                InterviewerReplyResult liveReply = callLiveInterviewerChat(
                        question, candidateMessage, currentCode, hintsUsed, latestAttempt
                );
                if (liveReply != null) {
                    return liveReply;
                }
            } catch (AiCallException e) {
                fallbackReason = e.getErrorCategory() + ": " + e.getMessage();
                log.warn("Live LLM chat call failed [{}], using deterministic interviewer response.", e.getErrorCategory());
            } catch (Exception e) {
                fallbackReason = "PROVIDER_ERROR: " + e.getClass().getSimpleName();
                log.warn("Live LLM chat call failed, using deterministic interviewer response: {}", e.getMessage());
            }
        } else {
            fallbackReason = "NOT_CONFIGURED: AI_API_KEY is not configured.";
        }

        return generateDeterministicInterviewerReply(
                question, candidateMessage, currentCode, hintsUsed, latestAttempt, fallbackReason
        );
    }

    private InterviewerReplyResult callLiveInterviewerChat(
            QuestionEntity question,
            String candidateMessage,
            String currentCode,
            int hintsUsed,
            AttemptEntity latestAttempt
    ) {
        String systemPrompt = """
                You are a professional, encouraging, rigorous senior technical interviewer conducting a Java coding interview.
                Rules:
                1. NEVER write out the complete Java solution or give away the exact answer key.
                2. Ask probing questions about algorithmic invariants, time/space complexity, and edge cases.
                3. Keep your response concise (2-5 sentences) and actionable.
                """;

        String attemptContext = latestAttempt == null
                ? "No code submissions yet."
                : String.format("Latest attempt status: %s, %d/%d tests passed, failure=%s",
                        latestAttempt.getCompilationStatus(),
                        latestAttempt.getTestsPassed(),
                        latestAttempt.getTestsTotal(),
                        latestAttempt.getFailureCategory());

        String userPrompt = "Problem: " + question.getTitle() + " (" + question.getTopic() + ", " + question.getDifficulty() + ")\n"
                + "Expected Complexity: Time " + question.getExpectedTimeComplexity() + ", Space " + question.getExpectedSpaceComplexity() + "\n"
                + "Expected Concepts: " + question.getExpectedConceptsJson() + "\n"
                + "Hints used so far: " + hintsUsed + "\n"
                + attemptContext + "\n"
                + "Candidate's Current Code:\n" + (currentCode != null ? currentCode : "") + "\n\n"
                + "Candidate says: " + candidateMessage;

        String content = aiProviderClient.chatCompletion(systemPrompt, userPrompt, 0.3);
        return new InterviewerReplyResult(content, "INTERVIEWER_REPLY", "ACTIVE_DISCUSSION", "LIVE_LLM", null);
    }

    private InterviewerReplyResult generateDeterministicInterviewerReply(
            QuestionEntity question,
            String candidateMessage,
            String currentCode,
            int hintsUsed,
            AttemptEntity latestAttempt,
            String fallbackReason
    ) {
        String lower = candidateMessage == null ? "" : candidateMessage.toLowerCase();

        if (lower.contains("complexity") || lower.contains("big o") || lower.contains("o(")) {
            return new InterviewerReplyResult(
                    String.format(
                            "Good focus on complexity. For **%s**, think about both the worst-case number of operations as input size grows and any auxiliary data structures allocated. Once you're ready, record your formal Time & Space complexity and rationale in the **Evaluate Explanation** tab so it's scored on your rubric.",
                            question.getTitle()
                    ),
                    "INTERVIEWER_REPLY",
                    "COMPLEXITY_DISCUSSION",
                    "FALLBACK_RUBRIC",
                    fallbackReason
            );
        }

        if (lower.contains("stuck") || lower.contains("help") || lower.contains("hint") || lower.contains("not sure") || lower.contains("approach")) {
            return new InterviewerReplyResult(
                    String.format(
                            "Let's break **%s** down systematically: this problem focuses on **%s**. Ask yourself: what information do we need to maintain as we iterate so we don't repeat redundant work? You can also click **Request Progressive Hint** (currently used %d) for a graduated clue without spoiling the full solution.",
                            question.getTitle(),
                            question.getTopic(),
                            hintsUsed
                    ),
                    "INTERVIEWER_REPLY",
                    "GUIDANCE",
                    "FALLBACK_RUBRIC",
                    fallbackReason
            );
        }

        if (lower.contains("edge case") || lower.contains("constraint") || lower.contains("boundary")) {
            return new InterviewerReplyResult(
                    "For boundary conditions on this problem, verify:\n- Minimum length inputs (e.g., empty or 1-element collections)\n- Duplicate elements or negative values where permitted by constraints\n- Off-by-one behavior on the first (`0`) and last (`n - 1`) indices.",
                    "INTERVIEWER_REPLY",
                    "EDGE_CASE_DISCUSSION",
                    "FALLBACK_RUBRIC",
                    fallbackReason
            );
        }

        if (latestAttempt != null && latestAttempt.getTestsPassed().equals(latestAttempt.getTestsTotal()) && latestAttempt.getTestsTotal() > 0) {
            return new InterviewerReplyResult(
                    String.format(
                            "Your reasoning makes sense and your implementation passes all %d test cases. Make sure to submit your structured explanation in the **Explain & Analyze** panel so we capture your Pattern Recognition, Complexity Analysis, and Communication scores before moving to the next question.",
                            latestAttempt.getTestsTotal()
                    ),
                    "INTERVIEWER_REPLY",
                    "WRAP_UP_PROMPT",
                    "FALLBACK_RUBRIC",
                    fallbackReason
            );
        }

        return new InterviewerReplyResult(
                String.format(
                        "That sounds like a reasonable direction for **%s** (%s). Consider how your state updates on each step and what invariant holds at the end of every iteration. Try translating that into the Java editor and running the sample tests to validate your hypothesis!",
                        question.getTitle(),
                        question.getTopic()
                ),
                "INTERVIEWER_REPLY",
                "ACTIVE_DISCUSSION",
                "FALLBACK_RUBRIC",
                fallbackReason
        );
    }
}
