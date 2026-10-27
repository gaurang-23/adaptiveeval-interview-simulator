package com.interviewsimulator.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.ai.dto.ExplanationEvaluationResult;
import com.interviewsimulator.backend.question.QuestionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExplanationEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ExplanationEvaluationService.class);

    private final ObjectMapper objectMapper;
    private final AiProviderClient aiProviderClient;

    @Autowired
    public ExplanationEvaluationService(
            ObjectMapper objectMapper,
            AiProviderClient aiProviderClient
    ) {
        this.objectMapper = objectMapper;
        this.aiProviderClient = aiProviderClient;
    }

    public ExplanationEvaluationService(
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl,
            String model,
            int timeoutMs
    ) {
        this(
                objectMapper,
                new AiProviderClient(objectMapper, "OPENAI_COMPATIBLE", apiKey, baseUrl, model, timeoutMs)
        );
    }

    public boolean isLiveLlmConfigured() {
        return aiProviderClient.isConfigured();
    }

    public ExplanationEvaluationResult evaluateExplanation(
            QuestionEntity question,
            String submittedCode,
            String algorithmExplanation,
            String timeComplexityClaim,
            String spaceComplexityClaim,
            String tradeOffsDiscussion
    ) {
        boolean timeMatch = matchesComplexity(timeComplexityClaim, question.getExpectedTimeComplexity());
        boolean spaceMatch = matchesComplexity(spaceComplexityClaim, question.getExpectedSpaceComplexity());

        String fallbackReason = null;
        if (aiProviderClient.shouldAttemptLiveCall()) {
            try {
                ExplanationEvaluationResult llmResult = evaluateViaLiveLlm(
                        question, submittedCode, algorithmExplanation, timeComplexityClaim,
                        spaceComplexityClaim, tradeOffsDiscussion, timeMatch, spaceMatch
                );
                if (llmResult != null) {
                    return llmResult;
                }
            } catch (AiCallException e) {
                fallbackReason = e.getErrorCategory() + ": " + e.getMessage();
                log.warn("Live LLM explanation evaluation failed [{}], falling back to deterministic rubric.", e.getErrorCategory());
            } catch (Exception e) {
                fallbackReason = "PROVIDER_ERROR: " + e.getClass().getSimpleName();
                log.warn("Live LLM explanation evaluation failed, falling back to deterministic rubric: {}", e.getMessage());
            }
        } else {
            fallbackReason = "NOT_CONFIGURED: AI_API_KEY is not configured.";
        }

        return evaluateViaDeterministicRubric(
                question, submittedCode, algorithmExplanation, timeComplexityClaim,
                spaceComplexityClaim, tradeOffsDiscussion, timeMatch, spaceMatch, fallbackReason
        );
    }

    private ExplanationEvaluationResult evaluateViaLiveLlm(
            QuestionEntity question,
            String submittedCode,
            String algorithmExplanation,
            String timeComplexityClaim,
            String spaceComplexityClaim,
            String tradeOffsDiscussion,
            boolean timeMatch,
            boolean spaceMatch
    ) {
        String systemPrompt = """
                You are a senior staff technical interviewer evaluating a candidate's algorithm explanation and complexity analysis.
                Respond ONLY with a valid JSON object matching this exact schema:
                {
                  "competency": "COMMUNICATION",
                  "score": 0-100,
                  "patternRecognitionScore": 0-100,
                  "complexityAnalysisScore": 0-100,
                  "communicationScore": 0-100,
                  "confidence": 0.4-0.95,
                  "evidence": "string citing concrete parts of the candidate's explanation",
                  "identified_misconceptions": ["string"],
                  "recommended_follow_up": "string follow-up question probing weak areas"
                }
                """;

        String userPrompt = "Problem: " + question.getTitle() + " (Topic: " + question.getTopic() + ")\n"
                + "Expected Time Complexity: " + question.getExpectedTimeComplexity() + "\n"
                + "Expected Space Complexity: " + question.getExpectedSpaceComplexity() + "\n"
                + "Expected Concepts: " + question.getExpectedConceptsJson() + "\n"
                + "Rubric: " + question.getRubricJson() + "\n\n"
                + "Candidate Code:\n" + (submittedCode != null ? submittedCode : "") + "\n\n"
                + "Candidate Explanation:\n" + (algorithmExplanation != null ? algorithmExplanation : "") + "\n"
                + "Claimed Time Complexity: " + timeComplexityClaim + "\n"
                + "Claimed Space Complexity: " + spaceComplexityClaim + "\n"
                + "Trade-offs / Edge Cases Discussed: " + (tradeOffsDiscussion != null ? tradeOffsDiscussion : "");

        JsonNode parsed = aiProviderClient.chatCompletionJson(systemPrompt, userPrompt, 0.2);

        double score = clamp(parsed.path("score").asDouble(70.0), 0.0, 100.0);
        double patternScore = clamp(parsed.path("patternRecognitionScore").asDouble(score), 0.0, 100.0);
        double complexityScore = clamp(parsed.path("complexityAnalysisScore").asDouble(score), 0.0, 100.0);
        double commScore = clamp(parsed.path("communicationScore").asDouble(score), 0.0, 100.0);
        double confidence = clamp(parsed.path("confidence").asDouble(0.8), 0.1, 0.99);
        String evidence = parsed.path("evidence").asText("Evaluated against problem rubric.");
        String followUp = parsed.path("recommended_follow_up").asText("Can you walk through a worst-case input for your approach?");

        List<String> misconceptions = new ArrayList<>();
        JsonNode miscNode = parsed.path("identified_misconceptions");
        if (miscNode.isArray()) {
            for (JsonNode item : miscNode) {
                if (!item.asText().isBlank()) {
                    misconceptions.add(item.asText());
                }
            }
        }

        return new ExplanationEvaluationResult(
                parsed.path("competency").asText("COMMUNICATION"),
                score,
                patternScore,
                complexityScore,
                commScore,
                confidence,
                evidence,
                misconceptions,
                followUp,
                timeMatch,
                spaceMatch,
                question.getExpectedTimeComplexity(),
                question.getExpectedSpaceComplexity(),
                "LIVE_LLM",
                null
        );
    }

    public ExplanationEvaluationResult evaluateViaDeterministicRubric(
            QuestionEntity question,
            String submittedCode,
            String algorithmExplanation,
            String timeComplexityClaim,
            String spaceComplexityClaim,
            String tradeOffsDiscussion,
            boolean timeMatch,
            boolean spaceMatch
    ) {
        return evaluateViaDeterministicRubric(
                question, submittedCode, algorithmExplanation, timeComplexityClaim,
                spaceComplexityClaim, tradeOffsDiscussion, timeMatch, spaceMatch, null
        );
    }

    public ExplanationEvaluationResult evaluateViaDeterministicRubric(
            QuestionEntity question,
            String submittedCode,
            String algorithmExplanation,
            String timeComplexityClaim,
            String spaceComplexityClaim,
            String tradeOffsDiscussion,
            boolean timeMatch,
            boolean spaceMatch,
            String fallbackReason
    ) {
        String explanation = (algorithmExplanation != null ? algorithmExplanation : "").trim();
        String tradeOffs = (tradeOffsDiscussion != null ? tradeOffsDiscussion : "").trim();
        String combinedText = (explanation + " " + tradeOffs).toLowerCase();

        List<String> expectedConcepts = parseConcepts(question.getExpectedConceptsJson());
        int matchedConcepts = 0;
        List<String> matchedNames = new ArrayList<>();
        List<String> missedConcepts = new ArrayList<>();

        for (String concept : expectedConcepts) {
            String[] keywords = concept.toLowerCase().split("[\\s/\\-]+");
            boolean hit = false;
            for (String kw : keywords) {
                if (kw.length() >= 3 && combinedText.contains(kw)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                matchedConcepts++;
                matchedNames.add(concept);
            } else {
                missedConcepts.add(concept);
            }
        }

        double conceptRatio = expectedConcepts.isEmpty() ? 0.7 : (double) matchedConcepts / expectedConcepts.size();
        int wordCount = explanation.isBlank() ? 0 : explanation.split("\\s+").length;

        // 1. Complexity Analysis Score (0-100)
        double complexityScore = 20.0;
        if (timeMatch) complexityScore += 40.0;
        if (spaceMatch) complexityScore += 30.0;
        if (combinedText.contains("because") || combinedText.contains("pass") || combinedText.contains("iteration")
                || combinedText.contains("memory") || combinedText.contains("auxiliary") || combinedText.contains("stack")
                || combinedText.contains("map") || combinedText.contains("pointer")) {
            complexityScore += 10.0;
        }
        complexityScore = clamp(complexityScore, 0.0, 100.0);

        // 2. Pattern Recognition Score from Explanation (0-100)
        double patternScore = 30.0 + (conceptRatio * 55.0);
        if (combinedText.contains("brute force") || combinedText.contains("optimize") || combinedText.contains("trade-off") || combinedText.contains("invariant")) {
            patternScore += 15.0;
        }
        if (wordCount < 8) {
            patternScore = Math.min(patternScore, 40.0);
        }
        patternScore = clamp(patternScore, 0.0, 100.0);

        // 3. Communication Score (0-100)
        double commScore = 20.0;
        if (wordCount >= 35) commScore += 35.0;
        else if (wordCount >= 18) commScore += 25.0;
        else if (wordCount >= 8) commScore += 12.0;

        commScore += conceptRatio * 25.0;
        if (!tradeOffs.isBlank() && tradeOffs.split("\\s+").length >= 6) {
            commScore += 20.0;
        }
        commScore = clamp(commScore, 0.0, 100.0);

        double overallScore = Math.round(((patternScore * 0.30) + (complexityScore * 0.35) + (commScore * 0.35)) * 10.0) / 10.0;
        double confidence = wordCount >= 15 ? 0.82 : 0.65;

        List<String> misconceptions = new ArrayList<>();
        if (!timeMatch) {
            misconceptions.add("Claimed time complexity '" + (timeComplexityClaim != null ? timeComplexityClaim : "unspecified")
                    + "' differs from expected optimal '" + question.getExpectedTimeComplexity() + "'.");
        }
        if (!spaceMatch) {
            misconceptions.add("Claimed space complexity '" + (spaceComplexityClaim != null ? spaceComplexityClaim : "unspecified")
                    + "' differs from expected '" + question.getExpectedSpaceComplexity() + "'.");
        }
        if (wordCount < 15) {
            misconceptions.add("Explanation is overly brief (" + wordCount + " words) and omits justification of correctness or invariants.");
        }
        if (tradeOffs.isBlank()) {
            misconceptions.add("Did not articulate design trade-offs or edge-case assumptions.");
        }

        StringBuilder evidenceBuilder = new StringBuilder();
        evidenceBuilder.append("Deterministic Rubric Evaluation: Explanation length = ").append(wordCount).append(" words; ");
        evidenceBuilder.append("Matched ").append(matchedConcepts).append("/").append(expectedConcepts.size()).append(" key concepts ");
        if (!matchedNames.isEmpty()) {
            evidenceBuilder.append("(").append(String.join(", ", matchedNames)).append("); ");
        }
        evidenceBuilder.append("Time complexity claim (").append(timeComplexityClaim).append(") ")
                .append(timeMatch ? "matched" : "mismatched").append(" expected ").append(question.getExpectedTimeComplexity()).append("; ");
        evidenceBuilder.append("Space complexity claim (").append(spaceComplexityClaim).append(") ")
                .append(spaceMatch ? "matched" : "mismatched").append(" expected ").append(question.getExpectedSpaceComplexity()).append(".");

        String followUp;
        if (!timeMatch) {
            followUp = "You estimated the time complexity as " + timeComplexityClaim
                    + ", whereas the target for this approach is " + question.getExpectedTimeComplexity()
                    + ". Can you trace how many times each element is processed in the worst case?";
        } else if (!spaceMatch) {
            followUp = "Let's double-check auxiliary space: what data structures or call stack frames grow with input size in your solution, and why does that yield "
                    + question.getExpectedSpaceComplexity() + "?";
        } else if (!missedConcepts.isEmpty()) {
            followUp = "Your complexity analysis is solid. How does your implementation specifically address "
                    + missedConcepts.get(0) + " and boundary inputs?";
        } else {
            followUp = "Great explanation of both the invariant and complexity. If the input size scaled 100x and could not fit in memory at once, what trade-offs would you make?";
        }

        return new ExplanationEvaluationResult(
                "COMMUNICATION",
                overallScore,
                patternScore,
                complexityScore,
                commScore,
                confidence,
                evidenceBuilder.toString(),
                misconceptions,
                followUp,
                timeMatch,
                spaceMatch,
                question.getExpectedTimeComplexity(),
                question.getExpectedSpaceComplexity(),
                "FALLBACK_RUBRIC",
                fallbackReason
        );
    }

    public boolean matchesComplexity(String claimed, String expected) {
        if (claimed == null || expected == null) return false;
        String normClaim = normalizeComplexity(claimed);
        String normExp = normalizeComplexity(expected);
        if (normClaim.isEmpty() || normExp.isEmpty()) return false;
        if (normClaim.equals(normExp) || normClaim.contains(normExp)) return true;
        if (normExp.equals("o(h)") && (normClaim.equals("o(n)") || normClaim.equals("o(logn)"))) return true;
        if (normExp.equals("o(min(n,m))") && (normClaim.equals("o(n)") || normClaim.equals("o(1)"))) return true;
        if (normExp.equals("o(amount*n)") && (normClaim.equals("o(n*amount)") || normClaim.equals("o(amount*coins)") || normClaim.equals("o(n*m)"))) return true;
        return false;
    }

    private String normalizeComplexity(String s) {
        return s.toLowerCase()
                .replaceAll("\\s+", "")
                .replace("×", "*")
                .replace("log(n)", "logn");
    }

    private List<String> parseConcepts(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<String> list = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) list.add(n.asText());
            }
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
