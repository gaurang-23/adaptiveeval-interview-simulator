package com.interviewsimulator.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @NotBlank @Size(min = 2, max = 120) String targetRole,
        @NotBlank @Size(min = 2, max = 60) String experienceLevel,
        @Size(max = 20) String themePreference
) {
    public UpdateProfileRequest(String displayName, String targetRole, String experienceLevel) {
        this(displayName, targetRole, experienceLevel, null);
    }
}
