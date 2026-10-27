package com.interviewsimulator.runner.dto;

public record TestCaseSpec(
        Long id,
        String inputData,
        String expectedOutput,
        boolean sample
) {}
