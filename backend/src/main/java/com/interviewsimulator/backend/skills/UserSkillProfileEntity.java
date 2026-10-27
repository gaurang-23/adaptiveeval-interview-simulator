package com.interviewsimulator.backend.skills;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_skill_profiles")
public class UserSkillProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String competency;

    @Column(nullable = false)
    private Double score = 50.0;

    @Column(nullable = false)
    private Double confidence = 0.0;

    @Column(name = "assessments_count", nullable = false)
    private Integer assessmentsCount = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCompetency() { return competency; }
    public void setCompetency(String competency) { this.competency = competency; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Integer getAssessmentsCount() { return assessmentsCount; }
    public void setAssessmentsCount(Integer assessmentsCount) { this.assessmentsCount = assessmentsCount; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
