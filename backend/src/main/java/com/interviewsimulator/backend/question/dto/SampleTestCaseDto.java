package com.interviewsimulator.backend.question.dto;

public record SampleTestCaseDto(
        Long id,
        String inputData,
        String expectedOutput,
        int orderIndex
) {}
