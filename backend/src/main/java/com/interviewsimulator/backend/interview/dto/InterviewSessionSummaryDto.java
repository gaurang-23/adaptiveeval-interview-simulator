package com.interviewsimulator.backend.interview.dto;

import java.time.Instant;
import java.util.List;

public record InterviewSessionSummaryDto(
        Long id,
        String interviewType,
        String configuredDifficulty,
        String interviewStyle,
        List<String> topics,
        int questionCount,
        int durationSeconds,
        Instant startedAt,
        Instant deadlineAt,
        Instant completedAt,
        String status,
        Double overallScore,
        Instant createdAt
) {}
