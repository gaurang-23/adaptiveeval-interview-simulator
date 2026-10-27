package com.interviewsimulator.backend.resume.dto;

import java.util.List;

public record ResumeAnalysisPageDto(
        List<ResumeAnalysisHistoryItemDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
