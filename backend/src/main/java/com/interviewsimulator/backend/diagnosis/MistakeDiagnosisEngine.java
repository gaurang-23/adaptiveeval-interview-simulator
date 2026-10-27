package com.interviewsimulator.backend.diagnosis;

import com.interviewsimulator.backend.ai.dto.ExplanationEvaluationResult;
import com.interviewsimulator.backend.common.CompetencyType;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.submission.AttemptEntity;
import com.interviewsimulator.backend.submission.SubmissionEvaluatorService.CodeStructuralSignals;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MistakeDiagnosisEngine {

    public static final double MIN_CONFIDENCE_THRESHOLD = 0.45;

    public List<CompetencyDiagnosis> diagnoseQuestionPerformance(
            QuestionEntity question,
            List<AttemptEntity> attempts,
            CodeStructuralSignals latestSignals,
            int hintsUsed,
            ExplanationEvaluationResult explanationResult
    ) {
        List<CompetencyDiagnosis> diagnoses = new ArrayList<>();

        diagnoses.add(diagnoseImplementation(question, attempts, latestSignals, hintsUsed));
        diagnoses.add(diagnosePatternRecognition(question, attempts, latestSignals, hintsUsed, explanationResult));
        diagnoses.add(diagnoseComplexityAnalysis(question, attempts, latestSignals, explanationResult));
        diagnoses.add(diagnoseDebugging(question, attempts, hintsUsed));
        diagnoses.add(diagnoseCommunication(question, explanationResult));

        return diagnoses;
    }

    private CompetencyDiagnosis diagnoseImplementation(
            QuestionEntity question,
            List<AttemptEntity> attempts,
            CodeStructuralSignals signals,
            int hintsUsed
    ) {
        if (attempts == null || attempts.isEmpty()) {
            return new CompetencyDiagnosis(
                    CompetencyType.IMPLEMENTATION,
                    50.0,
                    0.0,
                    false,
                    "INSUFFICIENT_EVIDENCE",
                    List.of("No code attempts submitted for this question."),
                    "Insufficient evidence to evaluate implementation competency.",
                    "Submit a code attempt to evaluate syntax, control flow, and boundary handling.",
                    "DETERMINISTIC"
            );
        }

        AttemptEntity latest = attempts.get(attempts.size() - 1);
        List<String> facts = new ArrayList<>();
        facts.add(String.format("Total code runs/submissions on '%s': %d", question.getTitle(), attempts.size()));
        facts.add(String.format("Latest compilation status: %s; Tests passed: %d/%d",
                latest.getCompilationStatus(), latest.getTestsPassed(), latest.getTestsTotal()));

        long compileErrorsCount = attempts.stream()
                .filter(a -> "COMPILATION_ERROR".equals(a.getCompilationStatus()))
                .count();
        if (compileErrorsCount > 0) {
            facts.add("Compilation errors occurred in " + compileErrorsCount + " attempt(s).");
        }

        double passRatio = latest.getTestsTotal() > 0
                ? (double) latest.getTestsPassed() / latest.getTestsTotal()
                : 0.0;

        double score;
        String code;
        String hypothesis;
        String intervention;

        if ("COMPILATION_ERROR".equals(latest.getCompilationStatus())) {
            score = 22.0;
            code = "SYNTAX_AND_TYPE_ERRORS";
            hypothesis = "Candidate struggled to translate logic into syntactically valid Java types and method signatures.";
            intervention = "Practice Java collection APIs (Map, Deque, Arrays) and verify return types before running.";
        } else if ("EDGE_CASE_FAILURE".equals(latest.getFailureCategory())) {
            score = 58.0 + (passRatio * 15.0) - (hintsUsed * 3.0);
            facts.add("Passed sample test cases but failed hidden edge-case tests.");
            code = "BOUNDARY_AND_EDGE_CASE_DEFECT";
            hypothesis = "Core loop logic works on nominal inputs, but boundary indices, empty/single-element inputs, or negative values are mishandled.";
            intervention = "Add explicit guard clauses for null/empty/single-element inputs and trace loop termination at index 0 and n - 1.";
        } else if ("RUNTIME_EXCEPTION".equals(latest.getFailureCategory())) {
            score = 35.0 + (passRatio * 20.0);
            facts.add("Encountered runtime exception during test execution.");
            code = "NULL_OR_INDEX_OUT_OF_BOUNDS";
            hypothesis = "Unchecked array index bounds or null pointer dereference during traversal.";
            intervention = "Check index arithmetic (e.g., i - k, i + 1) and null checks prior to dereferencing nodes or arrays.";
        } else if (latest.getTestsPassed().equals(latest.getTestsTotal()) && latest.getTestsTotal() > 0) {
            double attemptPenalty = Math.min(18.0, Math.max(0, attempts.size() - 2) * 4.0);
            double hintPenalty = Math.min(16.0, hintsUsed * 4.0);
            score = 94.0 - attemptPenalty - hintPenalty;
            code = "STRONG_IMPLEMENTATION";
            hypothesis = "Candidate translated the algorithm into clean, working Java code that handles both nominal and hidden boundary cases.";
            intervention = "Continue practicing concise Java collection idioms under timed conditions.";
        } else {
            score = 30.0 + (passRatio * 40.0) - (hintsUsed * 3.0);
            code = "LOGIC_IMPLEMENTATION_MISMATCH";
            hypothesis = "State updates or conditional branches inside the main loop do not preserve the required invariant.";
            intervention = "Trace variable state transitions by hand on a 3-element input before editing loop conditions.";
        }

        score = clamp(score, 5.0, 100.0);
        double confidence = attempts.size() >= 2 || Boolean.TRUE.equals(latest.getFinalSubmission()) ? 0.88 : 0.72;

        return new CompetencyDiagnosis(
                CompetencyType.IMPLEMENTATION,
                score,
                confidence,
                true,
                code,
                facts,
                hypothesis,
                intervention,
                "DETERMINISTIC"
        );
    }

    private CompetencyDiagnosis diagnosePatternRecognition(
            QuestionEntity question,
            List<AttemptEntity> attempts,
            CodeStructuralSignals signals,
            int hintsUsed,
            ExplanationEvaluationResult explanationResult
    ) {
        boolean hasAttempts = attempts != null && !attempts.isEmpty();
        boolean hasExplanation = explanationResult != null;

        if (!hasAttempts && !hasExplanation) {
            return new CompetencyDiagnosis(
                    CompetencyType.PATTERN_RECOGNITION,
                    50.0,
                    0.0,
                    false,
                    "INSUFFICIENT_EVIDENCE",
                    List.of("Neither code structure nor approach explanation was submitted."),
                    "Insufficient evidence to infer algorithmic pattern recognition.",
                    "Submit a solution or explain the chosen algorithmic pattern.",
                    "DETERMINISTIC"
            );
        }

        List<String> facts = new ArrayList<>();
        double codePatternScore = 60.0;

        if (signals != null && hasAttempts) {
            AttemptEntity latest = attempts.get(attempts.size() - 1);
            facts.add(String.format("Detected loop nesting depth: %d; Hash structure: %s; Stack/Queue: %s; Binary Search: %s; DP: %s",
                    signals.estimatedLoopDepth(),
                    signals.usesHashStructure(),
                    signals.usesStackOrQueue(),
                    signals.usesBinarySearchPattern(),
                    signals.usesDynamicProgramming()));

            if (signals.appearsSuboptimalForExpectedComplexity()) {
                facts.add("Code structure uses nested iteration where " + question.getExpectedTimeComplexity() + " pattern is expected.");
                codePatternScore = 42.0;
            } else if (latest.getTestsPassed().equals(latest.getTestsTotal()) && latest.getTestsTotal() > 0) {
                codePatternScore = 88.0;
            } else if (latest.getTestsPassed() > 0) {
                codePatternScore = 68.0;
            } else {
                codePatternScore = 45.0;
            }
        }

        if (hintsUsed >= 2) {
            facts.add("Requested " + hintsUsed + " progressive hints (including pattern-level guidance).");
            codePatternScore -= (hintsUsed * 6.0);
        }

        double finalScore = codePatternScore;
        String sourceType = "DETERMINISTIC";
        if (hasExplanation) {
            facts.add("Explanation pattern score from rubric evaluation: " + Math.round(explanationResult.patternRecognitionScore()));
            finalScore = hasAttempts
                    ? (codePatternScore * 0.55) + (explanationResult.patternRecognitionScore() * 0.45)
                    : explanationResult.patternRecognitionScore();
            sourceType = explanationResult.sourceType();
        }

        finalScore = clamp(finalScore, 10.0, 100.0);
        double confidence = (hasAttempts && hasExplanation) ? 0.86 : 0.68;

        String code;
        String hypothesis;
        String intervention;
        if (signals != null && signals.appearsSuboptimalForExpectedComplexity()) {
            code = "BRUTE_FORCE_OVER_OPTIMAL_PATTERN";
            hypothesis = "Candidate defaulted to a nested brute-force scan rather than recognizing the optimal " + question.getTopic() + " pattern.";
            intervention = "Review how " + question.getTopic() + " eliminates redundant inner-loop scans to achieve " + question.getExpectedTimeComplexity() + ".";
        } else if (hintsUsed >= 2 && finalScore < 70.0) {
            code = "HINT_DEPENDENT_PATTERN_IDENTIFICATION";
            hypothesis = "Candidate required multi-level hints to identify the core " + question.getTopic() + " paradigm.";
            intervention = "Practice classifying problem constraints into canonical patterns (" + question.getTopic() + ") before writing code.";
        } else if (finalScore >= 75.0) {
            code = "APPROPRIATE_PATTERN_SELECTED";
            hypothesis = "Candidate selected an appropriate " + question.getTopic() + " approach aligned with problem constraints.";
            intervention = "Challenge yourself with harder variants combining " + question.getTopic() + " with secondary data structures.";
        } else {
            code = "PARTIAL_PATTERN_ALIGNMENT";
            hypothesis = "Candidate partially identified the " + question.getTopic() + " approach but did not fully establish the invariant.";
            intervention = "Write down the state invariant for " + question.getTopic() + " problems prior to coding.";
        }

        return new CompetencyDiagnosis(
                CompetencyType.PATTERN_RECOGNITION,
                finalScore,
                confidence,
                true,
                code,
                facts,
                hypothesis,
                intervention,
                sourceType
        );
    }

    private CompetencyDiagnosis diagnoseComplexityAnalysis(
            QuestionEntity question,
            List<AttemptEntity> attempts,
            CodeStructuralSignals signals,
            ExplanationEvaluationResult explanationResult
    ) {
        boolean hasAttempts = attempts != null && !attempts.isEmpty();
        AttemptEntity latest = hasAttempts ? attempts.get(attempts.size() - 1) : null;
        boolean timedOut = latest != null && "TIME_LIMIT_EXCEEDED".equals(latest.getFailureCategory());
        boolean suboptimalStructure = signals != null && signals.appearsSuboptimalForExpectedComplexity();

        if (explanationResult == null && !timedOut && !suboptimalStructure) {
            return new CompetencyDiagnosis(
                    CompetencyType.COMPLEXITY_ANALYSIS,
                    50.0,
                    0.25,
                    false,
                    "INSUFFICIENT_EVIDENCE",
                    List.of("Candidate has not submitted a formal time and space complexity analysis."),
                    "Insufficient evidence to evaluate complexity analysis accuracy.",
                    "Always state and justify Big-O time and auxiliary space complexity after coding.",
                    "DETERMINISTIC"
            );
        }

        List<String> facts = new ArrayList<>();
        double score = explanationResult != null ? explanationResult.complexityAnalysisScore() : 45.0;

        if (explanationResult != null) {
            facts.add(String.format("Expected complexity: Time %s, Space %s",
                    question.getExpectedTimeComplexity(), question.getExpectedSpaceComplexity()));
            facts.add(String.format("Time complexity match: %s; Space complexity match: %s",
                    explanationResult.timeComplexityCorrect(), explanationResult.spaceComplexityCorrect()));
        }
        if (timedOut) {
            facts.add("Submission triggered TIME_LIMIT_EXCEEDED during test execution.");
            score = Math.min(score, 35.0);
        }
        if (suboptimalStructure) {
            facts.add("Static structure indicates nested loops exceeding target " + question.getExpectedTimeComplexity() + " complexity.");
            score = Math.min(score, 52.0);
        }

        score = clamp(score, 10.0, 100.0);
        double confidence = explanationResult != null ? 0.85 : 0.62;
        String sourceType = explanationResult != null ? explanationResult.sourceType() : "DETERMINISTIC";

        String code;
        String hypothesis;
        String intervention;
        if (timedOut || suboptimalStructure) {
            code = "SUBOPTIMAL_ALGORITHMIC_EFFICIENCY";
            hypothesis = "Implemented solution performs redundant work per element, failing to meet the " + question.getExpectedTimeComplexity() + " efficiency target.";
            intervention = "Analyze inner loop operations and replace linear lookups with O(1) hash structures or amortized two-pointer bounds.";
        } else if (explanationResult != null && (!explanationResult.timeComplexityCorrect() || !explanationResult.spaceComplexityCorrect())) {
            code = "INACCURATE_COMPLEXITY_ESTIMATE";
            hypothesis = "Candidate produced code but misidentified the asymptotic time or auxiliary space cost.";
            intervention = "Practice counting both loop iterations and auxiliary memory allocations (including recursion stack depth).";
        } else {
            code = "ACCURATE_COMPLEXITY_ANALYSIS";
            hypothesis = "Candidate accurately derived and justified both time (" + question.getExpectedTimeComplexity() + ") and space (" + question.getExpectedSpaceComplexity() + ") complexity.";
            intervention = "Continue analyzing amortized vs. worst-case complexity bounds.";
        }

        return new CompetencyDiagnosis(
                CompetencyType.COMPLEXITY_ANALYSIS,
                score,
                confidence,
                true,
                code,
                facts,
                hypothesis,
                intervention,
                sourceType
        );
    }

    private CompetencyDiagnosis diagnoseDebugging(
            QuestionEntity question,
            List<AttemptEntity> attempts,
            int hintsUsed
    ) {
        if (attempts == null || attempts.isEmpty()) {
            return new CompetencyDiagnosis(
                    CompetencyType.DEBUGGING,
                    50.0,
                    0.0,
                    false,
                    "INSUFFICIENT_EVIDENCE",
                    List.of("No execution attempts recorded."),
                    "Insufficient evidence to assess debugging process.",
                    "Run sample tests and iterate on failing cases to demonstrate debugging workflow.",
                    "DETERMINISTIC"
            );
        }

        boolean isDebuggingQuestion = "DEBUGGING".equalsIgnoreCase(question.getQuestionType());
        boolean hadInitialFailure = attempts.stream().anyMatch(a ->
                !"SUCCESS".equals(a.getCompilationStatus()) || !a.getTestsPassed().equals(a.getTestsTotal()));

        AttemptEntity latest = attempts.get(attempts.size() - 1);
        boolean eventuallyResolved = "SUCCESS".equals(latest.getCompilationStatus())
                && latest.getTestsTotal() > 0
                && latest.getTestsPassed().equals(latest.getTestsTotal());

        // If candidate passed on the very first attempt of a non-debugging question without any errors,
        // there was no failure to debug, unless it is explicitly a DEBUGGING problem!
        if (!isDebuggingQuestion && !hadInitialFailure && attempts.size() == 1) {
            return new CompetencyDiagnosis(
                    CompetencyType.DEBUGGING,
                    82.0,
                    0.52,
                    true,
                    "CLEAN_FIRST_PASS_VERIFICATION",
                    List.of("Solution compiled and passed all " + latest.getTestsTotal() + " tests on the first execution."),
                    "Candidate mentally dry-ran and verified boundary conditions prior to execution.",
                    "Try dedicated Debugging mode exercises to further test defect isolation skills.",
                    "DETERMINISTIC"
            );
        }

        List<String> facts = new ArrayList<>();
        facts.add("Question type: " + question.getQuestionType() + "; Total attempts: " + attempts.size());
        if (hadInitialFailure) {
            AttemptEntity first = attempts.get(0);
            facts.add(String.format("Initial attempt passed %d/%d tests (Category: %s); Final attempt passed %d/%d tests.",
                    first.getTestsPassed(), first.getTestsTotal(), first.getFailureCategory(),
                    latest.getTestsPassed(), latest.getTestsTotal()));
        }

        double score;
        String code;
        String hypothesis;
        String intervention;

        if (eventuallyResolved) {
            int extraIterations = Math.max(0, attempts.size() - 2);
            score = 88.0 - (extraIterations * 5.0) - (hintsUsed * 4.0);
            code = "EFFECTIVE_DEFECT_ISOLATION";
            hypothesis = "Candidate systematically interpreted test failures and isolated the root cause to achieve 100% pass rate.";
            intervention = "Minimize trial-and-error runs by tracing failing inputs against boundary conditions before re-running.";
        } else {
            AttemptEntity first = attempts.get(0);
            boolean improvedProgress = latest.getTestsPassed() > first.getTestsPassed();
            score = (improvedProgress ? 52.0 : 32.0) - Math.min(15.0, attempts.size() * 2.0);
            code = "UNRESOLVED_TEST_FAILURES";
            hypothesis = "Candidate made " + attempts.size() + " attempt(s) without isolating the root cause of the failing test cases.";
            intervention = "Construct a minimal 2- or 3-element counterexample from the failing test category and trace variable values line by line.";
        }

        score = clamp(score, 10.0, 100.0);
        double confidence = (isDebuggingQuestion || attempts.size() >= 2) ? 0.85 : 0.65;

        return new CompetencyDiagnosis(
                CompetencyType.DEBUGGING,
                score,
                confidence,
                true,
                code,
                facts,
                hypothesis,
                intervention,
                "DETERMINISTIC"
        );
    }

    private CompetencyDiagnosis diagnoseCommunication(
            QuestionEntity question,
            ExplanationEvaluationResult explanationResult
    ) {
        if (explanationResult == null) {
            return new CompetencyDiagnosis(
                    CompetencyType.COMMUNICATION,
                    50.0,
                    0.0,
                    false,
                    "INSUFFICIENT_EVIDENCE",
                    List.of("No algorithm or trade-off explanation was submitted for this question."),
                    "Insufficient evidence to evaluate technical communication.",
                    "Submit a clear explanation of your algorithm's invariant, complexity, and trade-offs for every question.",
                    "DETERMINISTIC"
            );
        }

        List<String> facts = new ArrayList<>();
        facts.add(explanationResult.evidence());
        if (!explanationResult.identifiedMisconceptions().isEmpty()) {
            facts.add("Identified gaps: " + String.join(" | ", explanationResult.identifiedMisconceptions()));
        }

        double score = clamp(explanationResult.communicationScore(), 10.0, 100.0);
        String code;
        String hypothesis;
        String intervention;

        if (score >= 75.0) {
            code = "CLEAR_TECHNICAL_EXPLANATION";
            hypothesis = "Candidate clearly articulated the algorithm's correctness invariant, complexity justification, and design trade-offs.";
            intervention = "Continue structuring explanations using Invariant -> Complexity -> Edge Cases & Trade-offs.";
        } else if (score >= 50.0) {
            code = "INCOMPLETE_JUSTIFICATION_OR_TRADEOFFS";
            hypothesis = "Candidate described what the code does procedurally but omitted key invariants, assumptions, or trade-off comparisons.";
            intervention = "Explain WHY the algorithm is correct and compare its time/space trade-offs against at least one alternative approach.";
        } else {
            code = "INSUFFICIENT_ALGORITHM_EXPLANATION";
            hypothesis = "Explanation was too brief or imprecise to justify correctness and complexity to a technical interviewer.";
            intervention = "Practice walking through your approach out loud or in writing using structured 3-part technical summaries.";
        }

        return new CompetencyDiagnosis(
                CompetencyType.COMMUNICATION,
                score,
                explanationResult.confidence(),
                explanationResult.confidence() >= MIN_CONFIDENCE_THRESHOLD,
                code,
                facts,
                hypothesis,
                intervention,
                explanationResult.sourceType()
        );
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
