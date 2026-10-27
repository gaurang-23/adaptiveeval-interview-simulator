package com.interviewsimulator.backend.question.dto;

import java.util.List;

public record QuestionSummaryDto(
        Long id,
        String title,
        String slug,
        String topic,
        String difficulty,
        String questionType,
        List<String> expectedConcepts
) {}
