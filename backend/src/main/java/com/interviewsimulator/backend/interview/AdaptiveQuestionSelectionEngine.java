package com.interviewsimulator.backend.interview;

import com.interviewsimulator.backend.common.CompetencyType;
import com.interviewsimulator.backend.common.DifficultyLevel;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.skills.UserSkillProfileEntity;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdaptiveQuestionSelectionEngine {

    public record SelectionDecision(
            QuestionEntity question,
            CompetencyType targetCompetency,
            DifficultyLevel targetDifficulty,
            String selectionReason
    ) {}

    public SelectionDecision selectNextQuestion(
            List<QuestionEntity> allActiveQuestions,
            List<UserSkillProfileEntity> userSkills,
            String interviewType,
            DifficultyLevel configuredDifficulty,
            List<String> requestedTopics,
            Set<Long> alreadyAssignedQuestionIds,
            Set<String> alreadyCoveredTopics,
            DifficultyLevel previousQuestionDifficulty,
            Double previousQuestionPassRatio,
            int previousQuestionHintsUsed,
            long remainingTimeSeconds
    ) {
        List<QuestionEntity> candidates = allActiveQuestions.stream()
                .filter(q -> !alreadyAssignedQuestionIds.contains(q.getId()))
                .toList();

        if (candidates.isEmpty()) {
            candidates = allActiveQuestions;
        }

        CompetencyType targetCompetency = determineTargetCompetency(userSkills, interviewType);
        DifficultyLevel targetDifficulty = determineBoundedDifficulty(
                configuredDifficulty,
                previousQuestionDifficulty,
                previousQuestionPassRatio,
                previousQuestionHintsUsed,
                remainingTimeSeconds
        );

        Set<String> normalizedRequestedTopics = requestedTopics == null
                ? Set.of()
                : requestedTopics.stream().map(s -> s.trim().toLowerCase()).collect(Collectors.toSet());

        Set<String> normalizedCoveredTopics = alreadyCoveredTopics == null
                ? Set.of()
                : alreadyCoveredTopics.stream().map(s -> s.trim().toLowerCase()).collect(Collectors.toSet());

        QuestionEntity bestQuestion = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (QuestionEntity q : candidates) {
            double score = scoreCandidateQuestion(
                    q,
                    targetCompetency,
                    targetDifficulty,
                    previousQuestionDifficulty,
                    interviewType,
                    normalizedRequestedTopics,
                    normalizedCoveredTopics
            );
            if (score > bestScore) {
                bestScore = score;
                bestQuestion = q;
            }
        }

        if (bestQuestion == null) {
            bestQuestion = candidates.get(0);
        }

        String reason = buildSelectionReason(
                bestQuestion,
                userSkills,
                targetCompetency,
                targetDifficulty,
                previousQuestionDifficulty,
                previousQuestionPassRatio,
                previousQuestionHintsUsed,
                remainingTimeSeconds
        );

        return new SelectionDecision(bestQuestion, targetCompetency, targetDifficulty, reason);
    }

    public CompetencyType determineTargetCompetency(List<UserSkillProfileEntity> userSkills, String interviewType) {
        if ("DEBUGGING".equalsIgnoreCase(interviewType)) {
            return CompetencyType.DEBUGGING;
        }
        if ("CONCEPTUAL".equalsIgnoreCase(interviewType)) {
            return CompetencyType.COMPLEXITY_ANALYSIS;
        }

        if (userSkills != null && !userSkills.isEmpty()) {
            var assessed = userSkills.stream()
                    .filter(s -> s.getAssessmentsCount() != null && s.getAssessmentsCount() > 0 && s.getConfidence() >= 0.15)
                    .min(Comparator.comparingDouble(UserSkillProfileEntity::getScore));
            if (assessed.isPresent()) {
                try {
                    return CompetencyType.valueOf(assessed.get().getCompetency());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return CompetencyType.PATTERN_RECOGNITION;
    }

    public DifficultyLevel determineBoundedDifficulty(
            DifficultyLevel configuredDifficulty,
            DifficultyLevel previousDifficulty,
            Double previousPassRatio,
            int previousHintsUsed,
            long remainingTimeSeconds
    ) {
        if (previousDifficulty == null) {
            return configuredDifficulty != null ? configuredDifficulty : DifficultyLevel.MEDIUM;
        }

        // Constrain difficulty if remaining interview time is short (< 6 minutes)
        if (remainingTimeSeconds > 0 && remainingTimeSeconds < 360) {
            return previousDifficulty.stepDown();
        }

        if (previousPassRatio == null) {
            return previousDifficulty;
        }

        if (previousPassRatio >= 1.0 && previousHintsUsed <= 1) {
            // Strong unassisted mastery -> bounded +1 step up
            return previousDifficulty.stepUp();
        }

        if (previousPassRatio >= 0.8 && previousHintsUsed >= 2) {
            // Solved after progressive hints -> maintain current difficulty rather than dropping trivially low
            return previousDifficulty;
        }

        if (previousPassRatio < 0.5) {
            // Struggled significantly -> bounded -1 step down
            return previousDifficulty.stepDown();
        }

        return previousDifficulty;
    }

    private double scoreCandidateQuestion(
            QuestionEntity q,
            CompetencyType targetCompetency,
            DifficultyLevel targetDifficulty,
            DifficultyLevel previousDifficulty,
            String interviewType,
            Set<String> requestedTopics,
            Set<String> coveredTopics
    ) {
        double score = 0.0;
        DifficultyLevel qDiff = DifficultyLevel.fromString(q.getDifficulty());

        // Enforce bounded difficulty step relative to previous question
        if (previousDifficulty != null && Math.abs(qDiff.getRank() - previousDifficulty.getRank()) > 1) {
            score -= 100.0;
        }

        if (qDiff == targetDifficulty) {
            score += 45.0;
        } else if (Math.abs(qDiff.getRank() - targetDifficulty.getRank()) == 1) {
            score += 20.0;
        }

        String qTopicLower = q.getTopic().trim().toLowerCase();
        if (requestedTopics.isEmpty() || requestedTopics.contains(qTopicLower)) {
            score += 35.0;
            if (!coveredTopics.contains(qTopicLower)) {
                score += 22.0; // Prioritize uncovered requested topics
            }
        }

        // Interview type alignment
        if (interviewType != null && !interviewType.equalsIgnoreCase("MIXED")) {
            if (q.getQuestionType().equalsIgnoreCase(interviewType)) {
                score += 30.0;
            }
        }

        // Competency alignment
        switch (targetCompetency) {
            case DEBUGGING -> {
                if ("DEBUGGING".equalsIgnoreCase(q.getQuestionType())) {
                    score += 40.0;
                }
            }
            case COMPLEXITY_ANALYSIS, COMMUNICATION -> {
                if ("CONCEPTUAL".equalsIgnoreCase(q.getQuestionType())
                        || "Dynamic Programming".equalsIgnoreCase(q.getTopic())
                        || "Binary Search".equalsIgnoreCase(q.getTopic())) {
                    score += 25.0;
                }
            }
            case PATTERN_RECOGNITION -> {
                if ("DSA".equalsIgnoreCase(q.getQuestionType())) {
                    score += 20.0;
                }
            }
            case IMPLEMENTATION -> {
                if ("Linked Lists".equalsIgnoreCase(q.getTopic())
                        || "Arrays".equalsIgnoreCase(q.getTopic())
                        || "Stacks".equalsIgnoreCase(q.getTopic())
                        || "Trees".equalsIgnoreCase(q.getTopic())) {
                    score += 18.0;
                }
            }
        }

        return score;
    }

    private String buildSelectionReason(
            QuestionEntity chosen,
            List<UserSkillProfileEntity> userSkills,
            CompetencyType targetCompetency,
            DifficultyLevel targetDifficulty,
            DifficultyLevel previousDifficulty,
            Double previousPassRatio,
            int previousHintsUsed,
            long remainingTimeSeconds
    ) {
        StringBuilder sb = new StringBuilder();
        var matchedSkill = userSkills == null ? null : userSkills.stream()
                .filter(s -> s.getCompetency().equals(targetCompetency.name()) && s.getAssessmentsCount() > 0)
                .findFirst()
                .orElse(null);

        if (matchedSkill != null) {
            sb.append(String.format("Targeted weakest assessed competency %s (score %.1f/100, confidence %.0f%%). ",
                    targetCompetency.getLabel(), matchedSkill.getScore(), matchedSkill.getConfidence() * 100));
        } else {
            sb.append(String.format("Selected to establish baseline evidence for %s. ", targetCompetency.getLabel()));
        }

        if (previousDifficulty == null) {
            sb.append(String.format("Initialized at configured %s difficulty on topic '%s'.",
                    chosen.getDifficulty(), chosen.getTopic()));
        } else {
            int pct = previousPassRatio != null ? (int) Math.round(previousPassRatio * 100) : 0;
            sb.append(String.format("Previous question pass rate was %d%% (%d hint(s) used); applied bounded difficulty policy (%s -> %s) covering topic '%s'.",
                    pct, previousHintsUsed, previousDifficulty.name(), chosen.getDifficulty(), chosen.getTopic()));
        }

        if (remainingTimeSeconds > 0 && remainingTimeSeconds < 360) {
            sb.append(" Adjusted scope for remaining session time.");
        }

        return sb.toString();
    }
}
