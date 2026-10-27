package com.interviewsimulator.backend.user.dto;

import java.time.Instant;

public record UserProfileDto(
        Long id,
        String email,
        String displayName,
        String targetRole,
        String experienceLevel,
        String preferredLanguage,
        String themePreference,
        Instant createdAt
) {
    public UserProfileDto(
            Long id,
            String email,
            String displayName,
            String targetRole,
            String experienceLevel,
            String preferredLanguage,
            Instant createdAt
    ) {
        this(id, email, displayName, targetRole, experienceLevel, preferredLanguage, null, createdAt);
    }
}
