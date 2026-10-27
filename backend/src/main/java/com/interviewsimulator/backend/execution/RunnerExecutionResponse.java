package com.interviewsimulator.backend.execution;

import java.util.List;

public record RunnerExecutionResponse(
        String compilationStatus,
        String compilerOutput,
        int totalTests,
        int passedTests,
        int failedTests,
        long runtimeMs,
        long memoryKb,
        String failureCategory,
        String sanitizedErrorOutput,
        String isolationModeUsed,
        List<RunnerTestCaseResult> testResults
) {
    public record RunnerTestCaseResult(
            Long testCaseId,
            int testIndex,
            boolean sample,
            boolean passed,
            String inputData,
            String expectedOutput,
            String actualOutput,
            String stdout,
            String errorMessage,
            long runtimeMs
    ) {}
}
