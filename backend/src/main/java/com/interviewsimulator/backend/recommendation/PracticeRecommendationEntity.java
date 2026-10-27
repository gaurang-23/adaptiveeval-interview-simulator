package com.interviewsimulator.backend.recommendation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "practice_recommendations")
public class PracticeRecommendationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "target_competency", nullable = false, length = 60)
    private String targetCompetency;

    @Column(name = "suggested_difficulty", nullable = false, length = 30)
    private String suggestedDifficulty;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "learning_objective", nullable = false, columnDefinition = "TEXT")
    private String learningObjective;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getTargetCompetency() { return targetCompetency; }
    public void setTargetCompetency(String targetCompetency) { this.targetCompetency = targetCompetency; }

    public String getSuggestedDifficulty() { return suggestedDifficulty; }
    public void setSuggestedDifficulty(String suggestedDifficulty) { this.suggestedDifficulty = suggestedDifficulty; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getLearningObjective() { return learningObjective; }
    public void setLearningObjective(String learningObjective) { this.learningObjective = learningObjective; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
