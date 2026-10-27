package com.interviewsimulator.backend.skills;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SkillAssessmentRepository extends JpaRepository<SkillAssessmentEntity, Long> {
    List<SkillAssessmentEntity> findByUserIdOrderByAssessedAtAsc(Long userId);
    List<SkillAssessmentEntity> findBySessionIdOrderByAssessedAtAsc(Long sessionId);
}
