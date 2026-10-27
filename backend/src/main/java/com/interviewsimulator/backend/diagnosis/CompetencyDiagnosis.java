package com.interviewsimulator.backend.diagnosis;

import com.interviewsimulator.backend.common.CompetencyType;
import java.util.List;

public record CompetencyDiagnosis(
        CompetencyType competency,
        double score,
        double confidence,
        boolean sufficientEvidence,
        String diagnosisCode,
        List<String> observedFacts,
        String inferredHypothesis,
        String recommendedIntervention,
        String sourceType
) {}
