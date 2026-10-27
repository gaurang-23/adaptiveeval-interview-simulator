package com.interviewsimulator.backend.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateInterviewRequest(
        @NotBlank(message = "Interview type is required")
        String interviewType,

        @NotBlank(message = "Difficulty is required")
        String difficulty,

        @NotEmpty(message = "Select at least one topic")
        List<String> topics,

        @Min(value = 15, message = "Minimum duration is 15 minutes")
        @Max(value = 60, message = "Maximum duration is 60 minutes")
        int durationMinutes,

        @Min(value = 1, message = "At least 1 question is required")
        @Max(value = 5, message = "Maximum 5 questions per session")
        int questionCount,

        @NotBlank(message = "Interview style is required")
        String interviewStyle,

        Long specificFirstQuestionId
) {}
