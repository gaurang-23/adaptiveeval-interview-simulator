package com.interviewsimulator.backend.interview;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "interview_sessions")
public class InterviewSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "interview_type", nullable = false, length = 40)
    private String interviewType;

    @Column(name = "configured_difficulty", nullable = false, length = 30)
    private String configuredDifficulty;

    @Column(name = "interview_style", nullable = false, length = 40)
    private String interviewStyle = "INTERVIEWER_LED";

    @Column(name = "topics_json", nullable = false, columnDefinition = "JSON")
    private String topicsJson;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount = 3;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds = 1800;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false, length = 40)
    private String status = "CREATED";

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "report_json", columnDefinition = "JSON")
    private String reportJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getInterviewType() { return interviewType; }
    public void setInterviewType(String interviewType) { this.interviewType = interviewType; }

    public String getConfiguredDifficulty() { return configuredDifficulty; }
    public void setConfiguredDifficulty(String configuredDifficulty) { this.configuredDifficulty = configuredDifficulty; }

    public String getInterviewStyle() { return interviewStyle; }
    public void setInterviewStyle(String interviewStyle) { this.interviewStyle = interviewStyle; }

    public String getTopicsJson() { return topicsJson; }
    public void setTopicsJson(String topicsJson) { this.topicsJson = topicsJson; }

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(Instant deadlineAt) { this.deadlineAt = deadlineAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }

    public String getReportJson() { return reportJson; }
    public void setReportJson(String reportJson) { this.reportJson = reportJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
