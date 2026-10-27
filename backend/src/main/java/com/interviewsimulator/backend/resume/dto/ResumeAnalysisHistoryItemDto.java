package com.interviewsimulator.backend.resume.dto;

import java.time.Instant;

public record ResumeAnalysisHistoryItemDto(
        Long id,
        String originalFilename,
        Long fileSizeBytes,
        String jobTitle,
        String jobDescriptionSummary,
        Integer overallScore,
        String analysisStatus,
        String aiProvider,
        String aiModel,
        String errorCategory,
        String errorMessage,
        Instant createdAt,
        Instant completedAt
) {}
