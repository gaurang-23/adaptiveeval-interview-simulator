package com.interviewsimulator.backend.interview.dto;

import com.interviewsimulator.backend.question.dto.QuestionDetailDto;
import java.time.Instant;
import java.util.List;

public record ActiveInterviewQuestionDto(
        Long interviewQuestionId,
        int sequenceNumber,
        int totalQuestions,
        String selectionReason,
        String targetCompetency,
        String status,
        int hintsUsed,
        List<String> unlockedHints,
        boolean explanationSubmitted,
        Instant startedAt,
        QuestionDetailDto question
) {}
