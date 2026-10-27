package com.interviewsimulator.backend.interview.dto;

import java.time.Instant;

public record InterviewMessageDto(
        Long id,
        Long questionId,
        String role,
        String content,
        String messageType,
        String sourceType,
        Instant createdAt
) {}
