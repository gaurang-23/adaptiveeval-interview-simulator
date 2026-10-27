package com.interviewsimulator.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.ai.ExplanationEvaluationService;
import com.interviewsimulator.backend.ai.dto.ExplanationEvaluationResult;
import com.interviewsimulator.backend.common.CompetencyType;
import com.interviewsimulator.backend.common.DifficultyLevel;
import com.interviewsimulator.backend.diagnosis.CompetencyDiagnosis;
import com.interviewsimulator.backend.diagnosis.MistakeDiagnosisEngine;
import com.interviewsimulator.backend.interview.AdaptiveQuestionSelectionEngine;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.skills.SkillAssessmentEntity;
import com.interviewsimulator.backend.skills.SkillAssessmentRepository;
import com.interviewsimulator.backend.skills.SkillUpdateEngine;
import com.interviewsimulator.backend.skills.UserSkillProfileEntity;
import com.interviewsimulator.backend.skills.UserSkillProfileRepository;
import com.interviewsimulator.backend.submission.AttemptEntity;
import com.interviewsimulator.backend.submission.SubmissionEvaluatorService.CodeStructuralSignals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoreEnginesUnitTest {

    private UserSkillProfileRepository profileRepository;
    private SkillAssessmentRepository assessmentRepository;
    private SkillUpdateEngine skillUpdateEngine;
    private MistakeDiagnosisEngine diagnosisEngine;
    private AdaptiveQuestionSelectionEngine selectionEngine;
    private ExplanationEvaluationService explanationEvaluationService;

    @BeforeEach
    void setUp() {
        profileRepository = mock(UserSkillProfileRepository.class);
        assessmentRepository = mock(SkillAssessmentRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        skillUpdateEngine = new SkillUpdateEngine(profileRepository, assessmentRepository, mapper);
        diagnosisEngine = new MistakeDiagnosisEngine();
        selectionEngine = new AdaptiveQuestionSelectionEngine();
        explanationEvaluationService = new ExplanationEvaluationService(mapper, "", "https://api.openai.com/v1", "gpt-4o-mini", 3000);
    }

    @Test
    void skillUpdateEngineAppliesWeightedFormulaAndSkipsInsufficientEvidence() {
        UserSkillProfileEntity existingImpl = new UserSkillProfileEntity();
        existingImpl.setUserId(10L);
        existingImpl.setCompetency(CompetencyType.IMPLEMENTATION.name());
        existingImpl.setScore(60.0);
        existingImpl.setConfidence(0.5);
        existingImpl.setAssessmentsCount(2);

        when(profileRepository.findByUserIdAndCompetency(10L, CompetencyType.IMPLEMENTATION.name()))
                .thenReturn(Optional.of(existingImpl));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assessmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompetencyDiagnosis validImplDiag = new CompetencyDiagnosis(
                CompetencyType.IMPLEMENTATION,
                90.0,
                0.85,
                true,
                "STRONG_IMPLEMENTATION",
                List.of("Passed 5/5 tests"),
                "Clean implementation",
                "Keep practicing",
                "DETERMINISTIC"
        );

        CompetencyDiagnosis insufficientCommDiag = new CompetencyDiagnosis(
                CompetencyType.COMMUNICATION,
                50.0,
                0.0,
                false,
                "INSUFFICIENT_EVIDENCE",
                List.of("No explanation submitted"),
                "Insufficient evidence",
                "Submit explanation",
                "DETERMINISTIC"
        );

        List<SkillAssessmentEntity> saved = skillUpdateEngine.applyDiagnosesAndUpdateSkills(
                10L, 100L, 1L, DifficultyLevel.MEDIUM, 0, List.of(validImplDiag, insufficientCommDiag)
        );

        assertEquals(1, saved.size(), "Only diagnosis with sufficient evidence should trigger a skill update");
        SkillAssessmentEntity sa = saved.get(0);
        assertEquals(CompetencyType.IMPLEMENTATION.name(), sa.getCompetency());
        assertEquals(60.0, sa.getPreviousScore());
        assertTrue(sa.getScore() > 60.0 && sa.getScore() < 90.0,
                "Updated score must follow weighted average s_new = alpha * r + (1 - alpha) * s");
        verify(profileRepository, never()).findByUserIdAndCompetency(10L, CompetencyType.COMMUNICATION.name());
    }

    @Test
    void adaptiveSelectionTargetsWeakestCompetencyAndEnforcesDifficultyBounds() {
        QuestionEntity qEasyArrays = buildQuestion(1L, "Easy Arrays", "Arrays", "EASY", "DSA");
        QuestionEntity qMedDebug = buildQuestion(2L, "Medium Debug", "Arrays", "MEDIUM", "DEBUGGING");
        QuestionEntity qHardDp = buildQuestion(3L, "Hard DP", "Dynamic Programming", "HARD", "DSA");

        UserSkillProfileEntity strongPattern = buildSkill(CompetencyType.PATTERN_RECOGNITION, 85.0, 0.8, 3);
        UserSkillProfileEntity weakDebug = buildSkill(CompetencyType.DEBUGGING, 38.0, 0.75, 2);

        // Previous question was HARD and candidate struggled (passRatio = 0.2) -> must step down to MEDIUM (not EASY!)
        var decision = selectionEngine.selectNextQuestion(
                List.of(qEasyArrays, qMedDebug, qHardDp),
                List.of(strongPattern, weakDebug),
                "MIXED",
                DifficultyLevel.HARD,
                List.of("Arrays", "Dynamic Programming"),
                Set.of(3L),
                Set.of("Dynamic Programming"),
                DifficultyLevel.HARD,
                0.2,
                1,
                1200L
        );

        assertEquals(CompetencyType.DEBUGGING, decision.targetCompetency());
        assertEquals(DifficultyLevel.MEDIUM, decision.targetDifficulty(), "Difficulty must step down by at most 1 level from HARD to MEDIUM");
        assertEquals(2L, decision.question().getId());
        assertTrue(decision.selectionReason().contains("Debugging"));
    }

    @Test
    void mistakeDiagnosisDistinguishesEdgeCaseFailureAndSuboptimalComplexity() {
        QuestionEntity q = buildQuestion(1L, "Two Sum", "Hash Maps", "EASY", "DSA");
        q.setExpectedTimeComplexity("O(n)");
        q.setExpectedSpaceComplexity("O(n)");
        q.setExpectedConceptsJson("[\"Hash Map\",\"Complement Lookup\"]");

        AttemptEntity edgeCaseAttempt = new AttemptEntity();
        edgeCaseAttempt.setCompilationStatus("SUCCESS");
        edgeCaseAttempt.setTestsPassed(2);
        edgeCaseAttempt.setTestsTotal(5);
        edgeCaseAttempt.setFailureCategory("EDGE_CASE_FAILURE");

        CodeStructuralSignals suboptimalSignals = new CodeStructuralSignals(
                2, false, false, false, false, false, false, true
        );

        ExplanationEvaluationResult evalResult = explanationEvaluationService.evaluateExplanation(
                q,
                "for(int i=0;i<n;i++) for(int j=i+1;j<n;j++)",
                "I used two nested loops to check every pair of numbers.",
                "O(n^2)",
                "O(1)",
                ""
        );

        List<CompetencyDiagnosis> diagnoses = diagnosisEngine.diagnoseQuestionPerformance(
                q, List.of(edgeCaseAttempt), suboptimalSignals, 0, evalResult
        );

        CompetencyDiagnosis implDiag = diagnoses.stream()
                .filter(d -> d.competency() == CompetencyType.IMPLEMENTATION)
                .findFirst().orElseThrow();
        assertEquals("BOUNDARY_AND_EDGE_CASE_DEFECT", implDiag.diagnosisCode());
        assertFalse(implDiag.observedFacts().isEmpty());

        CompetencyDiagnosis patternDiag = diagnoses.stream()
                .filter(d -> d.competency() == CompetencyType.PATTERN_RECOGNITION)
                .findFirst().orElseThrow();
        assertEquals("BRUTE_FORCE_OVER_OPTIMAL_PATTERN", patternDiag.diagnosisCode());

        CompetencyDiagnosis complexityDiag = diagnoses.stream()
                .filter(d -> d.competency() == CompetencyType.COMPLEXITY_ANALYSIS)
                .findFirst().orElseThrow();
        assertEquals("SUBOPTIMAL_ALGORITHMIC_EFFICIENCY", complexityDiag.diagnosisCode());
    }

    private QuestionEntity buildQuestion(Long id, String title, String topic, String difficulty, String type) {
        QuestionEntity q = new QuestionEntity();
        q.setId(id);
        q.setTitle(title);
        q.setSlug(title.toLowerCase().replace(" ", "-"));
        q.setTopic(topic);
        q.setDifficulty(difficulty);
        q.setQuestionType(type);
        q.setEntryMethodName("solve");
        return q;
    }

    private UserSkillProfileEntity buildSkill(CompetencyType comp, double score, double confidence, int count) {
        UserSkillProfileEntity s = new UserSkillProfileEntity();
        s.setCompetency(comp.name());
        s.setScore(score);
        s.setConfidence(confidence);
        s.setAssessmentsCount(count);
        return s;
    }
}
