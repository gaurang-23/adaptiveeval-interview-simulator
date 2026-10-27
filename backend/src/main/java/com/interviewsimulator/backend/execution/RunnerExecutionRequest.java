package com.interviewsimulator.backend.execution;

import java.util.List;

public record RunnerExecutionRequest(
        String sourceCode,
        String language,
        String entryMethodName,
        List<RunnerTestCaseSpec> testCases
) {
    public record RunnerTestCaseSpec(
            Long id,
            String inputData,
            String expectedOutput,
            boolean sample
    ) {}
}
