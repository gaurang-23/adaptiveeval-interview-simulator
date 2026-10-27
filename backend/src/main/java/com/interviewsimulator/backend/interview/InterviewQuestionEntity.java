package com.interviewsimulator.backend.interview;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "interview_questions")
public class InterviewQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "selection_reason", nullable = false, length = 500)
    private String selectionReason;

    @Column(name = "target_competency", nullable = false, length = 60)
    private String targetCompetency = "PATTERN_RECOGNITION";

    @Column(nullable = false, length = 40)
    private String status = "IN_PROGRESS";

    @Column(name = "hints_used", nullable = false)
    private Integer hintsUsed = 0;

    @Column(name = "explanation_submitted", nullable = false)
    private Boolean explanationSubmitted = false;

    @Column(name = "started_at")
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getSelectionReason() { return selectionReason; }
    public void setSelectionReason(String selectionReason) { this.selectionReason = selectionReason; }

    public String getTargetCompetency() { return targetCompetency; }
    public void setTargetCompetency(String targetCompetency) { this.targetCompetency = targetCompetency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getHintsUsed() { return hintsUsed; }
    public void setHintsUsed(Integer hintsUsed) { this.hintsUsed = hintsUsed; }

    public Boolean getExplanationSubmitted() { return explanationSubmitted; }
    public void setExplanationSubmitted(Boolean explanationSubmitted) { this.explanationSubmitted = explanationSubmitted; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
