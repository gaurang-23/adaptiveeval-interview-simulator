package com.interviewsimulator.backend.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Long> {
    List<ResumeAnalysisEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
