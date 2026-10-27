package com.interviewsimulator.backend.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.common.CompetencyType;
import com.interviewsimulator.backend.common.DifficultyLevel;
import com.interviewsimulator.backend.diagnosis.CompetencyDiagnosis;
import com.interviewsimulator.backend.diagnosis.MistakeDiagnosisEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class SkillUpdateEngine {

    private final UserSkillProfileRepository profileRepository;
    private final SkillAssessmentRepository assessmentRepository;
    private final ObjectMapper objectMapper;

    private static final Map<CompetencyType, Double> BASE_ALPHA = new EnumMap<>(CompetencyType.class);
    static {
        BASE_ALPHA.put(CompetencyType.PATTERN_RECOGNITION, 0.38);
        BASE_ALPHA.put(CompetencyType.IMPLEMENTATION, 0.42);
        BASE_ALPHA.put(CompetencyType.COMPLEXITY_ANALYSIS, 0.40);
        BASE_ALPHA.put(CompetencyType.DEBUGGING, 0.38);
        BASE_ALPHA.put(CompetencyType.COMMUNICATION, 0.40);
    }

    public SkillUpdateEngine(
            UserSkillProfileRepository profileRepository,
            SkillAssessmentRepository assessmentRepository,
            ObjectMapper objectMapper
    ) {
        this.profileRepository = profileRepository;
        this.assessmentRepository = assessmentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<SkillAssessmentEntity> applyDiagnosesAndUpdateSkills(
            Long userId,
            Long sessionId,
            Long questionId,
            DifficultyLevel difficulty,
            int hintsUsed,
            List<CompetencyDiagnosis> diagnoses
    ) {
        List<SkillAssessmentEntity> savedAssessments = new ArrayList<>();

        for (CompetencyDiagnosis diagnosis : diagnoses) {
            if (!diagnosis.sufficientEvidence() || diagnosis.confidence() < MistakeDiagnosisEngine.MIN_CONFIDENCE_THRESHOLD) {
                continue;
            }

            CompetencyType comp = diagnosis.competency();
            UserSkillProfileEntity profile = profileRepository
                    .findByUserIdAndCompetency(userId, comp.name())
                    .orElseGet(() -> {
                        UserSkillProfileEntity p = new UserSkillProfileEntity();
                        p.setUserId(userId);
                        p.setCompetency(comp.name());
                        p.setScore(50.0);
                        p.setConfidence(0.0);
                        p.setAssessmentsCount(0);
                        return p;
                    });

            double oldScore = profile.getScore();
            int currentCount = profile.getAssessmentsCount();

            double effectiveEvidenceScore = adjustEvidenceForDifficultyAndHints(
                    diagnosis.score(),
                    difficulty,
                    hintsUsed
            );

            double baseAlpha = BASE_ALPHA.getOrDefault(comp, 0.40);
            double alpha;
            if (currentCount == 0) {
                // First real evidence for this competency: weight observed performance strongly
                alpha = clamp(0.80 * diagnosis.confidence(), 0.50, 0.85);
            } else {
                alpha = clamp(baseAlpha * diagnosis.confidence() * difficulty.getDifficultyWeight(), 0.15, 0.60);
            }

            // s_new = alpha * r + (1 - alpha) * s
            double newScore = round1(clamp((alpha * effectiveEvidenceScore) + ((1.0 - alpha) * oldScore), 0.0, 100.0));
            double newConfidence = round2(clamp(
                    1.0 - ((1.0 - profile.getConfidence()) * (1.0 - (0.35 * diagnosis.confidence()))),
                    0.15,
                    0.98
            ));

            profile.setScore(newScore);
            profile.setConfidence(newConfidence);
            profile.setAssessmentsCount(currentCount + 1);
            profile.setUpdatedAt(Instant.now());
            profileRepository.save(profile);

            SkillAssessmentEntity assessment = new SkillAssessmentEntity();
            assessment.setUserId(userId);
            assessment.setSessionId(sessionId);
            assessment.setQuestionId(questionId);
            assessment.setCompetency(comp.name());
            assessment.setPreviousScore(oldScore);
            assessment.setScore(newScore);
            assessment.setConfidence(newConfidence);
            assessment.setEvidenceJson(serializeEvidence(diagnosis, effectiveEvidenceScore, alpha));
            assessment.setAssessedAt(Instant.now());

            savedAssessments.add(assessmentRepository.save(assessment));
        }

        return savedAssessments;
    }

    public double adjustEvidenceForDifficultyAndHints(
            double rawEvidenceScore,
            DifficultyLevel difficulty,
            int hintsUsed
    ) {
        double adjusted = rawEvidenceScore;
        if (difficulty == DifficultyLevel.HARD && rawEvidenceScore >= 60.0) {
            adjusted = Math.min(100.0, rawEvidenceScore * 1.08);
        } else if (difficulty == DifficultyLevel.EASY && rawEvidenceScore >= 85.0) {
            adjusted = Math.min(94.0, rawEvidenceScore * 0.96);
        }
        if (hintsUsed > 0) {
            double discount = Math.min(15.0, hintsUsed * 3.5);
            adjusted = Math.max(10.0, adjusted - discount);
        }
        return round1(clamp(adjusted, 0.0, 100.0));
    }

    private String serializeEvidence(CompetencyDiagnosis d, double adjustedEvidenceScore, double alphaUsed) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "diagnosisCode", d.diagnosisCode(),
                    "rawEvidenceScore", d.score(),
                    "adjustedEvidenceScore", adjustedEvidenceScore,
                    "alphaUsed", round2(alphaUsed),
                    "observedFacts", d.observedFacts(),
                    "inferredHypothesis", d.inferredHypothesis(),
                    "recommendedIntervention", d.recommendedIntervention(),
                    "sourceType", d.sourceType()
            ));
        } catch (Exception e) {
            return "{\"diagnosisCode\":\"" + d.diagnosisCode() + "\"}";
        }
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
