package com.interviewsimulator.runner.dto;

public record TestCaseExecutionResult(
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
