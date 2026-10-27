package com.interviewsimulator.backend.skills;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "skill_assessments")
public class SkillAssessmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(nullable = false, length = 60)
    private String competency;

    @Column(nullable = false)
    private Double score;

    @Column(name = "previous_score")
    private Double previousScore;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "JSON")
    private String evidenceJson;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getCompetency() { return competency; }
    public void setCompetency(String competency) { this.competency = competency; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public Double getPreviousScore() { return previousScore; }
    public void setPreviousScore(Double previousScore) { this.previousScore = previousScore; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }

    public Instant getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }
}
