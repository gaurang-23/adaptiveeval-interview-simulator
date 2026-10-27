package com.interviewsimulator.backend.recommendation;

import java.time.Instant;

public record RecommendationDto(
        Long id,
        Long questionId,
        String questionTitle,
        String questionSlug,
        String topic,
        String suggestedDifficulty,
        String questionType,
        String targetCompetency,
        String targetCompetencyLabel,
        String reason,
        String learningObjective,
        String status,
        Instant createdAt
) {}
