package com.interviewsimulator.backend.skills;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserSkillProfileRepository extends JpaRepository<UserSkillProfileEntity, Long> {
    List<UserSkillProfileEntity> findByUserIdOrderByCompetencyAsc(Long userId);
    Optional<UserSkillProfileEntity> findByUserIdAndCompetency(Long userId, String competency);
}
