package com.interviewsimulator.backend.ai.dto;

import java.util.List;

public record ExplanationEvaluationResult(
        String competency,
        double score,
        double patternRecognitionScore,
        double complexityAnalysisScore,
        double communicationScore,
        double confidence,
        String evidence,
        List<String> identifiedMisconceptions,
        String recommendedFollowUp,
        boolean timeComplexityCorrect,
        boolean spaceComplexityCorrect,
        String expectedTimeComplexity,
        String expectedSpaceComplexity,
        String sourceType,
        String fallbackReason
) {
    public ExplanationEvaluationResult(
            String competency,
            double score,
            double patternRecognitionScore,
            double complexityAnalysisScore,
            double communicationScore,
            double confidence,
            String evidence,
            List<String> identifiedMisconceptions,
            String recommendedFollowUp,
            boolean timeComplexityCorrect,
            boolean spaceComplexityCorrect,
            String expectedTimeComplexity,
            String expectedSpaceComplexity,
            String sourceType
    ) {
        this(
                competency,
                score,
                patternRecognitionScore,
                complexityAnalysisScore,
                communicationScore,
                confidence,
                evidence,
                identifiedMisconceptions,
                recommendedFollowUp,
                timeComplexityCorrect,
                spaceComplexityCorrect,
                expectedTimeComplexity,
                expectedSpaceComplexity,
                sourceType,
                null
        );
    }
}
