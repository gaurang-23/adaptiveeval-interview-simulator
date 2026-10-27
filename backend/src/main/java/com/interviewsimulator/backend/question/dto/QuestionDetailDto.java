package com.interviewsimulator.backend.question.dto;

import java.util.List;

public record QuestionDetailDto(
        Long id,
        String title,
        String slug,
        String topic,
        String difficulty,
        String questionType,
        String entryMethodName,
        String statement,
        String constraintsText,
        List<QuestionExampleDto> examples,
        String starterCode,
        int totalHintsAvailable,
        List<SampleTestCaseDto> sampleTestCases
) {}
