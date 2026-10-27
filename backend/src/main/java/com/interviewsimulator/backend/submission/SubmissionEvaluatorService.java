package com.interviewsimulator.backend.submission;

import com.interviewsimulator.backend.execution.CodeExecutionClient;
import com.interviewsimulator.backend.execution.RunnerExecutionRequest;
import com.interviewsimulator.backend.execution.RunnerExecutionResponse;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.question.QuestionTestCaseEntity;
import com.interviewsimulator.backend.question.QuestionTestCaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SubmissionEvaluatorService {

    private final CodeExecutionClient executionClient;
    private final QuestionTestCaseRepository testCaseRepository;

    public SubmissionEvaluatorService(
            CodeExecutionClient executionClient,
            QuestionTestCaseRepository testCaseRepository
    ) {
        this.executionClient = executionClient;
        this.testCaseRepository = testCaseRepository;
    }

    public record CodeStructuralSignals(
            int estimatedLoopDepth,
            boolean usesHashStructure,
            boolean usesStackOrQueue,
            boolean usesBinarySearchPattern,
            boolean usesDynamicProgramming,
            boolean usesRecursion,
            boolean hasNullOrBoundaryGuard,
            boolean appearsSuboptimalForExpectedComplexity
    ) {}

    public RunnerExecutionResponse evaluateCode(QuestionEntity question, String sourceCode, boolean sampleOnly) {
        List<QuestionTestCaseEntity> testCases = sampleOnly
                ? testCaseRepository.findByQuestionIdAndSampleTrueOrderByOrderIndexAsc(question.getId())
                : testCaseRepository.findByQuestionIdOrderByOrderIndexAsc(question.getId());

        List<RunnerExecutionRequest.RunnerTestCaseSpec> specs = testCases.stream()
                .map(tc -> new RunnerExecutionRequest.RunnerTestCaseSpec(
                        tc.getId(),
                        tc.getInputData(),
                        tc.getExpectedOutput(),
                        Boolean.TRUE.equals(tc.getSample())
                ))
                .toList();

        RunnerExecutionRequest req = new RunnerExecutionRequest(
                sourceCode,
                "JAVA",
                question.getEntryMethodName(),
                specs
        );

        RunnerExecutionResponse raw = executionClient.executeJavaSubmission(req);

        // Mask hidden test case inputs and expected outputs so they are never exposed to the client
        List<RunnerExecutionResponse.RunnerTestCaseResult> maskedResults = raw.testResults() == null
                ? List.of()
                : raw.testResults().stream()
                .map(tr -> tr.sample()
                        ? tr
                        : new RunnerExecutionResponse.RunnerTestCaseResult(
                                tr.testCaseId(),
                                tr.testIndex(),
                                false,
                                tr.passed(),
                                "[Hidden Test Case #" + (tr.testIndex() + 1) + "]",
                                "[Hidden]",
                                tr.passed() ? "[Matched Expected]" : "[Output Mismatch]",
                                tr.stdout(),
                                tr.errorMessage(),
                                tr.runtimeMs()
                        ))
                .toList();

        return new RunnerExecutionResponse(
                raw.compilationStatus(),
                raw.compilerOutput(),
                raw.totalTests(),
                raw.passedTests(),
                raw.failedTests(),
                raw.runtimeMs(),
                raw.memoryKb(),
                raw.failureCategory(),
                raw.sanitizedErrorOutput(),
                raw.isolationModeUsed(),
                maskedResults
        );
    }

    public CodeStructuralSignals analyzeStructure(QuestionEntity question, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return new CodeStructuralSignals(0, false, false, false, false, false, false, false);
        }
        String stripped = sourceCode.replaceAll("//.*", "").replaceAll("(?s)/\\*.*?\\*/", "");
        int loopDepth = estimateLoopDepth(stripped);
        boolean usesHash = stripped.contains("HashMap") || stripped.contains("HashSet") || stripped.contains("Map<") || stripped.contains("Set<");
        boolean usesStackQueue = stripped.contains("Deque") || stripped.contains("ArrayDeque") || stripped.contains("Stack") || stripped.contains("Queue") || stripped.contains("PriorityQueue");
        boolean usesBinSearch = (stripped.contains("low") || stripped.contains("left")) && stripped.contains("mid") && stripped.contains("/ 2");
        boolean usesDp = stripped.contains("dp[") || stripped.contains("memo") || stripped.contains("tails[");
        boolean usesRecursion = countOccurrences(stripped, "\\b" + Pattern.quote(question.getEntryMethodName()) + "\\s*\\(") >= 2
                || stripped.contains("dfs(") || stripped.contains("bfs(");
        boolean hasGuard = stripped.contains("== null") || stripped.contains("!= null")
                || stripped.contains(".isEmpty()") || stripped.contains(".length == 0");

        String expectedTime = question.getExpectedTimeComplexity() == null ? "" : question.getExpectedTimeComplexity().toLowerCase();
        boolean expectsLinearOrLog = expectedTime.equals("o(n)") || expectedTime.equals("o(log n)") || expectedTime.equals("o(n log n)");
        boolean suboptimal = false;
        if (expectsLinearOrLog && loopDepth >= 2 && !question.getTopic().equalsIgnoreCase("Graphs") && !question.getTopic().equalsIgnoreCase("Dynamic Programming")) {
            if (expectedTime.equals("o(log n)") && loopDepth >= 1 && !usesBinSearch) {
                suboptimal = true;
            } else if (expectedTime.equals("o(n)") && loopDepth >= 2 && !usesHash && ! stripped.contains("left") && !stripped.contains("right")) {
                suboptimal = true;
            }
        }
        if (expectedTime.equals("o(log n)") && !usesBinSearch && loopDepth >= 1) {
            suboptimal = true;
        }

        return new CodeStructuralSignals(
                loopDepth,
                usesHash,
                usesStackQueue,
                usesBinSearch,
                usesDp,
                usesRecursion,
                hasGuard,
                suboptimal
        );
    }

    private int estimateLoopDepth(String code) {
        Pattern loopPattern = Pattern.compile("\\b(for|while)\\s*\\(");
        Matcher m = loopPattern.matcher(code);
        int loopCount = 0;
        while (m.find()) {
            loopCount++;
        }
        if (loopCount == 0) return 0;
        if (loopCount == 1) return 1;
        // Check if one loop appears inside another block before closing brace
        int currentLoops = 0;
        int maxNestedLoops = 1;
        String[] lines = code.split("\\R");
        int braceDepth = 0;
        int firstLoopBraceDepth = -1;
        for (String line : lines) {
            if (loopPattern.matcher(line).find()) {
                if (firstLoopBraceDepth != -1 && braceDepth > firstLoopBraceDepth) {
                    maxNestedLoops = Math.max(maxNestedLoops, 2);
                } else {
                    firstLoopBraceDepth = braceDepth;
                }
            }
            for (char c : line.toCharArray()) {
                if (c == '{') braceDepth++;
                else if (c == '}') {
                    braceDepth--;
                    if (braceDepth <= firstLoopBraceDepth) {
                        firstLoopBraceDepth = -1;
                    }
                }
            }
        }
        return maxNestedLoops;
    }

    private int countOccurrences(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }
}
