package com.interviewsimulator.backend.analytics.dto;

import com.interviewsimulator.backend.interview.dto.InterviewSessionSummaryDto;
import com.interviewsimulator.backend.recommendation.RecommendationDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardDto(
        boolean hasAssessmentData,
        int totalInterviewsCompleted,
        int activeInterviewsCount,
        Double averageInterviewScore,
        Double currentOverallProficiency,
        Double improvementSincePrevious,
        String aiMode,
        boolean liveLlmConfigured,
        Map<String, Object> runnerHealth,
        List<SkillMetricDto> skillProfile,
        List<SkillMetricDto> weakestCompetencies,
        List<SkillMetricDto> strongestCompetencies,
        List<SkillProgressionPointDto> skillProgression,
        List<TopicPerformanceDto> topicPerformance,
        List<SessionTrendPointDto> correctnessAndEfficiencyTrends,
        List<InterviewSessionSummaryDto> recentActivity,
        List<RecommendationDto> recommendedPractice
) {
    public record SkillMetricDto(
            String competency,
            String label,
            double score,
            double confidence,
            int assessmentsCount,
            boolean sufficientEvidence,
            Instant updatedAt
    ) {}

    public record SkillProgressionPointDto(
            Long sessionId,
            String sessionLabel,
            Instant timestamp,
            Double patternRecognition,
            Double implementation,
            Double complexityAnalysis,
            Double debugging,
            Double communication
    ) {}

    public record TopicPerformanceDto(
            String topic,
            int attemptsCount,
            int questionsAttempted,
            int testsPassed,
            int testsTotal,
            double accuracyPercent,
            long avgRuntimeMs
    ) {}

    public record SessionTrendPointDto(
            Long sessionId,
            String sessionLabel,
            Instant completedAt,
            double overallScore,
            double testPassRatePercent,
            double compilationSuccessPercent,
            long avgRuntimeMs
    ) {}
}
