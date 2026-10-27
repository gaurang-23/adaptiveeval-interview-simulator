package com.interviewsimulator.backend.interview.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank(message = "Message content cannot be empty")
        String content,
        String currentCode
) {}
