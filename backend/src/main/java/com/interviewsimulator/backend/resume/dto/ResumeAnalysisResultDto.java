package com.interviewsimulator.backend.resume.dto;

import java.util.List;

public record ResumeAnalysisResultDto(
        int overallScore,
        String summary,
        String disclaimer,
        List<DimensionScoreItem> dimensionScores,
        List<StrengthItem> strengths,
        List<WeaknessItem> weaknesses,
        List<RequirementMatchItem> matchedRequirements,
        List<PartialRequirementMatchItem> partiallyMatchedRequirements,
        List<MissingRequirementItem> missingRequirements,
        List<TechnicalSkillItem> technicalSkills,
        ExperienceAssessment experienceAssessment,
        ProjectAssessment projectAssessment,
        List<ImprovementRecommendationItem> improvementRecommendations,
        List<SuggestedResumeEditItem> suggestedResumeEdits,
        List<InterviewPrepTopicItem> interviewPreparationTopics
) {
    public record DimensionScoreItem(
            String dimensionKey,
            String label,
            int score,
            int maxScore,
            String rationale
    ) {}

    public record StrengthItem(
            String title,
            String resumeEvidence,
            String relatedRequirement
    ) {}

    public record WeaknessItem(
            String gap,
            String detail,
            String relatedRequirement,
            String actionableStep
    ) {}

    public record RequirementMatchItem(
            String requirement,
            String resumeEvidence
    ) {}

    public record PartialRequirementMatchItem(
            String requirement,
            String resumeEvidence,
            String missingDetail
    ) {}

    public record MissingRequirementItem(
            String requirement,
            String statusNote
    ) {}

    public record TechnicalSkillItem(
            String skill,
            String alignmentStatus,
            String resumeEvidence
    ) {}

    public record ExperienceAssessment(
            String summary,
            List<String> relevantResponsibilities,
            String seniorityAndScopeNote
    ) {}

    public record ProjectAssessment(
            String summary,
            List<String> keyProjectsAndOutcomes,
            String measurableImpactNote
    ) {}

    public record ImprovementRecommendationItem(
            String priority,
            String targetSection,
            String recommendation,
            String rationale
    ) {}

    public record SuggestedResumeEditItem(
            String section,
            String originalContext,
            String suggestedTruthfulWording,
            String alignmentReason
    ) {}

    public record InterviewPrepTopicItem(
            String topic,
            String reason,
            String sampleQuestion
    ) {}
}
