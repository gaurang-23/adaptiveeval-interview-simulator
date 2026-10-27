package com.interviewsimulator.backend.auth.dto;

import com.interviewsimulator.backend.user.dto.UserProfileDto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UserProfileDto user
) {}
