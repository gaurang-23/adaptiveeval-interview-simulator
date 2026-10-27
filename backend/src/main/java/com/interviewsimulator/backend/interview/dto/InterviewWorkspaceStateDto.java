package com.interviewsimulator.backend.interview.dto;

import java.time.Instant;
import java.util.List;

public record InterviewWorkspaceStateDto(
        InterviewSessionSummaryDto session,
        long remainingSeconds,
        Instant serverNow,
        String aiMode,
        boolean liveLlmConfigured,
        ActiveInterviewQuestionDto activeQuestion,
        List<AttemptDto> currentQuestionAttempts,
        List<InterviewMessageDto> messages
) {}
