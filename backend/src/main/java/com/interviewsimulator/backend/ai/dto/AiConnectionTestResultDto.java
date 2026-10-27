package com.interviewsimulator.backend.ai.dto;

import com.interviewsimulator.backend.ai.AiConnectionStatus;
import java.time.Instant;

public record AiConnectionTestResultDto(
        String provider,
        String model,
        String baseUrl,
        boolean configured,
        boolean liveLlmConfigured,
        String aiMode,
        AiConnectionStatus connectionStatus,
        String errorCategory,
        String message,
        String troubleshootingRecommendation,
        Instant lastTestedAt,
        Long latencyMs
) {}
