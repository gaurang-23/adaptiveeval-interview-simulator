package com.interviewsimulator.backend.resume.dto;

import java.time.Instant;
import java.util.List;

public record ResumeAnalysisReportDto(
        Long id,
        String originalFilename,
        String fileContentType,
        Long fileSizeBytes,
        String jobTitle,
        String jobDescription,
        Integer overallScore,
        String analysisStatus,
        String aiProvider,
        String aiModel,
        String errorCategory,
        String errorMessage,
        Instant createdAt,
        Instant completedAt,
        List<ResumeAnalysisResultDto.DimensionScoreItem> dimensionScores,
        ResumeAnalysisResultDto result
) {}
