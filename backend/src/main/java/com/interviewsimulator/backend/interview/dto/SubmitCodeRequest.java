package com.interviewsimulator.backend.interview.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitCodeRequest(
        @NotBlank(message = "Code cannot be empty")
        String code,

        String language,

        boolean sampleOnly
) {}
