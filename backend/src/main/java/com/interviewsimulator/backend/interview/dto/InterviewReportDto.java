package com.interviewsimulator.backend.interview.dto;

import com.interviewsimulator.backend.recommendation.RecommendationDto;
import java.time.Instant;
import java.util.List;

public record InterviewReportDto(
        Long sessionId,
        String interviewType,
        String configuredDifficulty,
        String interviewStyle,
        Instant startedAt,
        Instant completedAt,
        String status,
        double overallScore,
        String aiModeUsed,
        PreviousSessionComparison previousComparison,
        List<CompetencyBreakdownItem> competencyScores,
        List<String> strongestCompetencies,
        List<String> weakestCompetencies,
        List<TopicBreakdownItem> topicResults,
        List<QuestionAttemptReportItem> questionReports,
        List<DiagnosedMistakeItem> diagnosedMistakes,
        List<RecommendationDto> recommendedPractice
) {
    public record PreviousSessionComparison(
            Long previousSessionId,
            double previousOverallScore,
            double scoreDelta,
            Instant previousCompletedAt
    ) {}

    public record CompetencyBreakdownItem(
            String competency,
            String label,
            double score,
            Double previousScore,
            Double delta,
            double confidence,
            boolean sufficientEvidence,
            String sourceType
    ) {}

    public record TopicBreakdownItem(
            String topic,
            int questionsAttempted,
            int totalTestsPassed,
            int totalTestsRun,
            double averageScore
    ) {}

    public record QuestionAttemptReportItem(
            Long questionId,
            int sequenceNumber,
            String title,
            String topic,
            String difficulty,
            String selectionReason,
            String targetCompetency,
            int attemptsCount,
            String finalCompilationStatus,
            int testsPassed,
            int testsTotal,
            long bestRuntimeMs,
            String expectedTimeComplexity,
            String expectedSpaceComplexity,
            int hintsUsed,
            boolean explanationSubmitted
    ) {}

    public record DiagnosedMistakeItem(
            Long questionId,
            String questionTitle,
            String competency,
            String competencyLabel,
            String diagnosisCode,
            double score,
            double confidence,
            List<String> observedFacts,
            String inferredHypothesis,
            String recommendedIntervention,
            String sourceType
    ) {}
}
