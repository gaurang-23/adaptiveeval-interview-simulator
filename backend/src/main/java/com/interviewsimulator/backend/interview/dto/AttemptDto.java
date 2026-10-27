package com.interviewsimulator.backend.interview.dto;

import com.interviewsimulator.backend.execution.RunnerExecutionResponse.RunnerTestCaseResult;
import java.time.Instant;
import java.util.List;

public record AttemptDto(
        Long id,
        Long questionId,
        String submittedCode,
        String language,
        boolean finalSubmission,
        String compilationStatus,
        String compilerOutput,
        int testsPassed,
        int testsTotal,
        long runtimeMs,
        long memoryKb,
        String failureCategory,
        String isolationModeUsed,
        List<RunnerTestCaseResult> testResults,
        Instant submittedAt
) {}
