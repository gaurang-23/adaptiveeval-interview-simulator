package com.interviewsimulator.runner.dto;

import java.util.List;

public record ExecutionResponse(
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
        List<TestCaseExecutionResult> testResults
) {}
