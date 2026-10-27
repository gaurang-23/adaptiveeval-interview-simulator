package com.interviewsimulator.backend.recommendation;

import com.interviewsimulator.backend.common.CompetencyType;
import com.interviewsimulator.backend.common.DifficultyLevel;
import com.interviewsimulator.backend.common.ForbiddenOperationException;
import com.interviewsimulator.backend.common.ResourceNotFoundException;
import com.interviewsimulator.backend.interview.InterviewSessionEntity;
import com.interviewsimulator.backend.interview.InterviewSessionRepository;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.question.QuestionRepository;
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
public class RecommendationService {

    private final PracticeRecommendationRepository recommendationRepository;
    private final UserSkillProfileRepository skillProfileRepository;
    private final QuestionRepository questionRepository;
    private final InterviewSessionRepository sessionRepository;
    private final AttemptRepository attemptRepository;

    public RecommendationService(
            PracticeRecommendationRepository recommendationRepository,
            UserSkillProfileRepository skillProfileRepository,
            QuestionRepository questionRepository,
            InterviewSessionRepository sessionRepository,
            AttemptRepository attemptRepository
    ) {
        this.recommendationRepository = recommendationRepository;
        this.skillProfileRepository = skillProfileRepository;
        this.questionRepository = questionRepository;
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional
    public List<RecommendationDto> getOrGenerateRecommendations(Long userId) {
        List<PracticeRecommendationEntity> existing = recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (existing.isEmpty()) {
            refreshRecommendationsForUser(userId);
            existing = recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        Map<Long, QuestionEntity> questionMap = questionRepository.findAll().stream()
                .collect(Collectors.toMap(QuestionEntity::getId, q -> q));

        return existing.stream()
                .map(rec -> toDto(rec, questionMap.get(rec.getQuestionId())))
                .toList();
    }

    @Transactional
    public List<RecommendationDto> refreshRecommendationsForUser(Long userId) {
        List<UserSkillProfileEntity> profiles = skillProfileRepository.findByUserIdOrderByCompetencyAsc(userId);
        List<QuestionEntity> allQuestions = questionRepository.findByActiveTrueOrderByIdAsc();
        if (allQuestions.isEmpty()) return List.of();

        List<InterviewSessionEntity> userSessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Set<Long> masteredQuestionIds = new HashSet<>();
        Set<Long> failedQuestionIds = new HashSet<>();

        for (InterviewSessionEntity session : userSessions) {
            List<AttemptEntity> attempts = attemptRepository.findBySessionIdOrderBySubmittedAtAsc(session.getId());
            for (AttemptEntity a : attempts) {
                if ("SUCCESS".equals(a.getCompilationStatus()) && a.getTestsTotal() > 0 && a.getTestsPassed().equals(a.getTestsTotal())) {
                    masteredQuestionIds.add(a.getQuestionId());
                    failedQuestionIds.remove(a.getQuestionId());
                } else if (!masteredQuestionIds.contains(a.getQuestionId())) {
                    failedQuestionIds.add(a.getQuestionId());
                }
            }
        }

        // Remove old PENDING recommendations before regenerating fresh ones
        recommendationRepository.deleteByUserIdAndStatus(userId, "PENDING");

        List<UserSkillProfileEntity> sortedByWeakest = new ArrayList<>(profiles);
        sortedByWeakest.sort(Comparator
                .comparing((UserSkillProfileEntity p) -> p.getAssessmentsCount() == 0 ? 1 : 0)
                .thenComparingDouble(UserSkillProfileEntity::getScore));

        Set<Long> chosenQuestionIds = new HashSet<>();
        List<PracticeRecommendationEntity> created = new ArrayList<>();

        for (UserSkillProfileEntity profile : sortedByWeakest) {
            if (created.size() >= 4) break;

            CompetencyType comp;
            try {
                comp = CompetencyType.valueOf(profile.getCompetency());
            } catch (IllegalArgumentException e) {
                comp = CompetencyType.PATTERN_RECOGNITION;
            }

            DifficultyLevel suggestedDiff = determineRecommendedDifficulty(profile);
            QuestionEntity candidate = pickBestQuestionForCompetency(
                    allQuestions, comp, suggestedDiff, masteredQuestionIds, failedQuestionIds, chosenQuestionIds
            );

            if (candidate != null) {
                chosenQuestionIds.add(candidate.getId());
                PracticeRecommendationEntity rec = new PracticeRecommendationEntity();
                rec.setUserId(userId);
                rec.setQuestionId(candidate.getId());
                rec.setTargetCompetency(comp.name());
                rec.setSuggestedDifficulty(candidate.getDifficulty());
                rec.setTopic(candidate.getTopic());
                rec.setReason(buildRecommendationReason(profile, comp, candidate, failedQuestionIds.contains(candidate.getId())));
                rec.setLearningObjective(buildLearningObjective(comp, candidate));
                rec.setStatus("PENDING");
                rec.setCreatedAt(Instant.now());
                created.add(recommendationRepository.save(rec));
            }
        }

        Map<Long, QuestionEntity> questionMap = allQuestions.stream()
                .collect(Collectors.toMap(QuestionEntity::getId, q -> q));
        return created.stream()
                .map(r -> toDto(r, questionMap.get(r.getQuestionId())))
                .toList();
    }

    @Transactional
    public RecommendationDto updateRecommendationStatus(Long userId, Long recommendationId, String newStatus) {
        PracticeRecommendationEntity rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found."));
        if (!rec.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("You do not have permission to modify this recommendation.");
        }
        rec.setStatus(newStatus.trim().toUpperCase());
        PracticeRecommendationEntity saved = recommendationRepository.save(rec);
        QuestionEntity q = questionRepository.findById(saved.getQuestionId()).orElse(null);
        return toDto(saved, q);
    }

    private DifficultyLevel determineRecommendedDifficulty(UserSkillProfileEntity profile) {
        if (profile.getAssessmentsCount() == 0) {
            return DifficultyLevel.EASY;
        }
        if (profile.getScore() < 55.0) return DifficultyLevel.EASY;
        if (profile.getScore() < 78.0) return DifficultyLevel.MEDIUM;
        return DifficultyLevel.HARD;
    }

    private QuestionEntity pickBestQuestionForCompetency(
            List<QuestionEntity> allQuestions,
            CompetencyType comp,
            DifficultyLevel targetDiff,
            Set<Long> masteredIds,
            Set<Long> failedIds,
            Set<Long> alreadyChosenIds
    ) {
        QuestionEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (QuestionEntity q : allQuestions) {
            if (alreadyChosenIds.contains(q.getId())) continue;
            double score = 0.0;
            if (!masteredIds.contains(q.getId())) {
                score += 50.0;
            }
            if (failedIds.contains(q.getId())) {
                score += 25.0; // Intentional reinforcement on previously failed problem
            }

            DifficultyLevel qDiff = DifficultyLevel.fromString(q.getDifficulty());
            if (qDiff == targetDiff) score += 30.0;
            else if (Math.abs(qDiff.getRank() - targetDiff.getRank()) == 1) score += 12.0;

            if (comp == CompetencyType.DEBUGGING && "DEBUGGING".equalsIgnoreCase(q.getQuestionType())) {
                score += 40.0;
            } else if ((comp == CompetencyType.COMPLEXITY_ANALYSIS || comp == CompetencyType.COMMUNICATION)
                    && ("CONCEPTUAL".equalsIgnoreCase(q.getQuestionType()) || "Dynamic Programming".equalsIgnoreCase(q.getTopic()))) {
                score += 25.0;
            } else if (comp == CompetencyType.PATTERN_RECOGNITION && "DSA".equalsIgnoreCase(q.getQuestionType())) {
                score += 20.0;
            }

            if (score > bestScore) {
                bestScore = score;
                best = q;
            }
        }
        return best;
    }

    private String buildRecommendationReason(
            UserSkillProfileEntity profile,
            CompetencyType comp,
            QuestionEntity q,
            boolean previouslyFailed
    ) {
        if (profile.getAssessmentsCount() == 0) {
            return String.format("No assessment evidence recorded yet for %s. Practicing '%s' (%s) will establish an initial baseline.",
                    comp.getLabel(), q.getTitle(), q.getTopic());
        }
        if (previouslyFailed) {
            return String.format("Reinforcement recommended: you previously encountered test failures on '%s', and your %s score is %.1f/100 (confidence %.0f%%).",
                    q.getTitle(), comp.getLabel(), profile.getScore(), profile.getConfidence() * 100);
        }
        return String.format("Your current %s score is %.1f/100 (confidence %.0f%%). Practicing '%s' (%s · %s) targets this skill directly.",
                comp.getLabel(), profile.getScore(), profile.getConfidence() * 100, q.getTitle(), q.getTopic(), q.getDifficulty());
    }

    private String buildLearningObjective(CompetencyType comp, QuestionEntity q) {
        return switch (comp) {
            case PATTERN_RECOGNITION -> String.format("Identify why the %s pattern achieves %s time complexity on '%s' without brute-force scanning.",
                    q.getTopic(), q.getExpectedTimeComplexity(), q.getTitle());
            case IMPLEMENTATION -> String.format("Implement '%s' in Java with clean index bounds, null/empty guards, and zero compilation errors.",
                    q.getTitle());
            case COMPLEXITY_ANALYSIS -> String.format("Derive and justify the %s time and %s auxiliary space bounds for '%s'.",
                    q.getExpectedTimeComplexity(), q.getExpectedSpaceComplexity(), q.getTitle());
            case DEBUGGING -> String.format("Systematically isolate boundary defects and verify hypotheses using sample test cases on '%s'.",
                    q.getTitle());
            case COMMUNICATION -> String.format("Articulate the loop invariant, correctness proof, and design trade-offs for '%s'.",
                    q.getTitle());
        };
    }

    private RecommendationDto toDto(PracticeRecommendationEntity rec, QuestionEntity q) {
        String label = rec.getTargetCompetency();
        try {
            label = CompetencyType.valueOf(rec.getTargetCompetency()).getLabel();
        } catch (IllegalArgumentException ignored) {}

        return new RecommendationDto(
                rec.getId(),
                rec.getQuestionId(),
                q != null ? q.getTitle() : "Question #" + rec.getQuestionId(),
                q != null ? q.getSlug() : "",
                rec.getTopic(),
                rec.getSuggestedDifficulty(),
                q != null ? q.getQuestionType() : "DSA",
                rec.getTargetCompetency(),
                label,
                rec.getReason(),
                rec.getLearningObjective(),
                rec.getStatus(),
                rec.getCreatedAt()
        );
    }
}
