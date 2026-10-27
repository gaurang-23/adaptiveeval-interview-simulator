package com.interviewsimulator.backend.interview.dto;

import java.time.Instant;
import java.util.List;

public record HintResponseDto(
        int hintLevel,
        int totalHintsAvailable,
        String hintText,
        List<String> unlockedHints,
        Instant requestedAt
) {}
