package com.interviewsimulator.backend.interview.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitExplanationRequest(
        @NotBlank(message = "Algorithm explanation is required")
        String algorithmExplanation,

        @NotBlank(message = "Time complexity claim is required")
        String timeComplexity,

        @NotBlank(message = "Space complexity claim is required")
        String spaceComplexity,

        String tradeOffs
) {}
