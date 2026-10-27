package com.interviewsimulator.backend.analytics;

import com.interviewsimulator.backend.ai.AiInterviewerService;
import com.interviewsimulator.backend.analytics.dto.DashboardDto;
import com.interviewsimulator.backend.analytics.dto.DashboardDto.*;
import com.interviewsimulator.backend.common.CompetencyType;
import com.interviewsimulator.backend.execution.CodeExecutionClient;
import com.interviewsimulator.backend.interview.InterviewOrchestrationService;
import com.interviewsimulator.backend.interview.InterviewSessionEntity;
import com.interviewsimulator.backend.interview.InterviewSessionRepository;
import com.interviewsimulator.backend.interview.dto.InterviewSessionSummaryDto;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.question.QuestionRepository;
import com.interviewsimulator.backend.recommendation.RecommendationDto;
import com.interviewsimulator.backend.recommendation.RecommendationService;
import com.interviewsimulator.backend.skills.SkillAssessmentEntity;
import com.interviewsimulator.backend.skills.SkillAssessmentRepository;
import com.interviewsimulator.backend.skills.UserSkillProfileEntity;
import com.interviewsimulator.backend.skills.UserSkillProfileRepository;
import com.interviewsimulator.backend.submission.AttemptEntity;
import com.interviewsimulator.backend.submission.AttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final UserSkillProfileRepository skillProfileRepository;
    private final SkillAssessmentRepository skillAssessmentRepository;
    private final InterviewSessionRepository sessionRepository;
    private final AttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final RecommendationService recommendationService;
    private final InterviewOrchestrationService orchestrationService;
    private final AiInterviewerService aiInterviewerService;
    private final CodeExecutionClient executionClient;

    public AnalyticsService(
            UserSkillProfileRepository skillProfileRepository,
            SkillAssessmentRepository skillAssessmentRepository,
            InterviewSessionRepository sessionRepository,
            AttemptRepository attemptRepository,
            QuestionRepository questionRepository,
            RecommendationService recommendationService,
            InterviewOrchestrationService orchestrationService,
            AiInterviewerService aiInterviewerService,
            CodeExecutionClient executionClient
    ) {
        this.skillProfileRepository = skillProfileRepository;
        this.skillAssessmentRepository = skillAssessmentRepository;
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.recommendationService = recommendationService;
        this.orchestrationService = orchestrationService;
        this.aiInterviewerService = aiInterviewerService;
        this.executionClient = executionClient;
    }

    @Transactional
    public DashboardDto getDashboardData(Long userId) {
        List<InterviewSessionSummaryDto> allSessions = orchestrationService.listUserInterviews(userId);
        List<InterviewSessionSummaryDto> completedSessions = allSessions.stream()
                .filter(s -> "COMPLETED".equals(s.status()))
                .toList();
        int activeCount = (int) allSessions.stream()
                .filter(s -> "IN_PROGRESS".equals(s.status()) || "CREATED".equals(s.status()))
                .count();

        List<SkillMetricDto> skillMetrics = getUserSkills(userId);
        List<SkillMetricDto> assessedSkills = skillMetrics.stream()
                .filter(SkillMetricDto::sufficientEvidence)
                .toList();

        boolean hasAssessmentData = !completedSessions.isEmpty() || !assessedSkills.isEmpty();

        Double avgInterviewScore = null;
        if (!completedSessions.isEmpty()) {
            avgInterviewScore = round1(completedSessions.stream()
                    .filter(s -> s.overallScore() != null)
                    .mapToDouble(InterviewSessionSummaryDto::overallScore)
                    .average()
                    .orElse(0.0));
        }

        Double currentOverallProficiency = null;
        if (!assessedSkills.isEmpty()) {
            currentOverallProficiency = round1(assessedSkills.stream()
                    .mapToDouble(SkillMetricDto::score)
                    .average()
                    .orElse(0.0));
        }

        Double improvementSincePrevious = null;
        List<InterviewSessionSummaryDto> scoredCompleted = completedSessions.stream()
                .filter(s -> s.overallScore() != null)
                .toList();
        if (scoredCompleted.size() >= 2) {
            double latest = scoredCompleted.get(0).overallScore();
            double previous = scoredCompleted.get(1).overallScore();
            improvementSincePrevious = round1(latest - previous);
        }

        List<SkillMetricDto> weakest = assessedSkills.stream()
                .sorted(Comparator.comparingDouble(SkillMetricDto::score))
                .limit(2)
                .toList();

        List<SkillMetricDto> strongest = assessedSkills.stream()
                .sorted(Comparator.comparingDouble(SkillMetricDto::score).reversed())
                .limit(2)
                .toList();

        List<SkillProgressionPointDto> progression = buildSkillProgression(userId, allSessions);
        List<TopicPerformanceDto> topicPerf = buildTopicPerformance(userId);
        List<SessionTrendPointDto> trends = buildSessionTrends(userId);
        List<RecommendationDto> recommendations = recommendationService.getOrGenerateRecommendations(userId);

        return new DashboardDto(
                hasAssessmentData,
                completedSessions.size(),
                activeCount,
                avgInterviewScore,
                currentOverallProficiency,
                improvementSincePrevious,
                aiInterviewerService.getActiveAiMode(),
                aiInterviewerService.isLiveLlmAvailable(),
                executionClient.checkRunnerHealth(),
                skillMetrics,
                weakest,
                strongest,
                progression,
                topicPerf,
                trends,
                allSessions.stream().limit(10).toList(),
                recommendations
        );
    }

    @Transactional(readOnly = true)
    public List<SkillMetricDto> getUserSkills(Long userId) {
        List<UserSkillProfileEntity> profiles = skillProfileRepository.findByUserIdOrderByCompetencyAsc(userId);
        Map<String, UserSkillProfileEntity> byComp = profiles.stream()
                .collect(Collectors.toMap(UserSkillProfileEntity::getCompetency, p -> p, (a, b) -> a));

        List<SkillMetricDto> out = new ArrayList<>();
        for (CompetencyType comp : CompetencyType.values()) {
            UserSkillProfileEntity p = byComp.get(comp.name());
            if (p != null) {
                boolean hasEvidence = p.getAssessmentsCount() != null && p.getAssessmentsCount() > 0;
                out.add(new SkillMetricDto(
                        comp.name(),
                        comp.getLabel(),
                        hasEvidence ? round1(p.getScore()) : 0.0,
                        round2(p.getConfidence()),
                        p.getAssessmentsCount() != null ? p.getAssessmentsCount() : 0,
                        hasEvidence,
                        p.getUpdatedAt()
                ));
            } else {
                out.add(new SkillMetricDto(
                        comp.name(),
                        comp.getLabel(),
                        0.0,
                        0.0,
                        0,
                        false,
                        Instant.now()
                ));
            }
        }
        return out;
    }

    private List<SkillProgressionPointDto> buildSkillProgression(
            Long userId,
            List<InterviewSessionSummaryDto> allSessions
    ) {
        List<SkillAssessmentEntity> assessments = skillAssessmentRepository.findByUserIdOrderByAssessedAtAsc(userId);
        if (assessments.isEmpty()) return List.of();

        Map<Long, List<SkillAssessmentEntity>> bySession = new LinkedHashMap<>();
        for (SkillAssessmentEntity sa : assessments) {
            bySession.computeIfAbsent(sa.getSessionId(), k -> new ArrayList<>()).add(sa);
        }

        Map<String, Double> runningScores = new HashMap<>();
        List<SkillProgressionPointDto> timeline = new ArrayList<>();
        int index = 1;

        for (var entry : bySession.entrySet()) {
            Long sessionId = entry.getKey();
            Instant ts = Instant.now();
            for (SkillAssessmentEntity sa : entry.getValue()) {
                runningScores.put(sa.getCompetency(), round1(sa.getScore()));
                ts = sa.getAssessedAt();
            }
            timeline.add(new SkillProgressionPointDto(
                    sessionId,
                    "Session #" + index++,
                    ts,
                    runningScores.get(CompetencyType.PATTERN_RECOGNITION.name()),
                    runningScores.get(CompetencyType.IMPLEMENTATION.name()),
                    runningScores.get(CompetencyType.COMPLEXITY_ANALYSIS.name()),
                    runningScores.get(CompetencyType.DEBUGGING.name()),
                    runningScores.get(CompetencyType.COMMUNICATION.name())
            ));
        }

        return timeline;
    }

    private List<TopicPerformanceDto> buildTopicPerformance(Long userId) {
        List<InterviewSessionEntity> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (sessions.isEmpty()) return List.of();

        Map<Long, QuestionEntity> questionMap = questionRepository.findAll().stream()
                .collect(Collectors.toMap(QuestionEntity::getId, q -> q));

        Map<String, List<AttemptEntity>> attemptsByTopic = new LinkedHashMap<>();
        for (InterviewSessionEntity s : sessions) {
            List<AttemptEntity> attempts = attemptRepository.findBySessionIdOrderBySubmittedAtAsc(s.getId());
            for (AttemptEntity a : attempts) {
                QuestionEntity q = questionMap.get(a.getQuestionId());
                if (q != null) {
                    attemptsByTopic.computeIfAbsent(q.getTopic(), k -> new ArrayList<>()).add(a);
                }
            }
        }

        List<TopicPerformanceDto> result = new ArrayList<>();
        for (var entry : attemptsByTopic.entrySet()) {
            List<AttemptEntity> attempts = entry.getValue();
            int uniqueQuestions = (int) attempts.stream().map(AttemptEntity::getQuestionId).distinct().count();
            int passed = attempts.stream().mapToInt(AttemptEntity::getTestsPassed).sum();
            int total = attempts.stream().mapToInt(AttemptEntity::getTestsTotal).sum();
            double acc = total > 0 ? round1(passed * 100.0 / total) : 0.0;
            long avgRuntime = (long) attempts.stream()
                    .filter(a -> a.getRuntimeMs() != null && a.getRuntimeMs() > 0)
                    .mapToLong(AttemptEntity::getRuntimeMs)
                    .average()
                    .orElse(0.0);

            result.add(new TopicPerformanceDto(
                    entry.getKey(),
                    attempts.size(),
                    uniqueQuestions,
                    passed,
                    total,
                    acc,
                    avgRuntime
            ));
        }
        return result;
    }

    private List<SessionTrendPointDto> buildSessionTrends(Long userId) {
        List<InterviewSessionEntity> completed = new ArrayList<>(
                sessionRepository.findByUserIdAndStatusOrderByCompletedAtDesc(userId, "COMPLETED")
        );
        Collections.reverse(completed);

        List<SessionTrendPointDto> points = new ArrayList<>();
        int idx = 1;
        for (InterviewSessionEntity s : completed) {
            List<AttemptEntity> attempts = attemptRepository.findBySessionIdOrderBySubmittedAtAsc(s.getId());
            int totalTests = attempts.stream().mapToInt(AttemptEntity::getTestsTotal).sum();
            int passedTests = attempts.stream().mapToInt(AttemptEntity::getTestsPassed).sum();
            long compileSuccess = attempts.stream()
                    .filter(a -> "SUCCESS".equals(a.getCompilationStatus()))
                    .count();
            long avgRuntime = (long) attempts.stream()
                    .filter(a -> a.getRuntimeMs() != null && a.getRuntimeMs() > 0)
                    .mapToLong(AttemptEntity::getRuntimeMs)
                    .average()
                    .orElse(0.0);

            double passRate = totalTests > 0 ? round1(passedTests * 100.0 / totalTests) : 0.0;
            double compileRate = !attempts.isEmpty() ? round1(compileSuccess * 100.0 / attempts.size()) : 0.0;

            points.add(new SessionTrendPointDto(
                    s.getId(),
                    "Interview #" + idx++,
                    s.getCompletedAt() != null ? s.getCompletedAt() : s.getCreatedAt(),
                    s.getOverallScore() != null ? round1(s.getOverallScore()) : 0.0,
                    passRate,
                    compileRate,
                    avgRuntime
            ));
        }
        return points;
    }

    private double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
