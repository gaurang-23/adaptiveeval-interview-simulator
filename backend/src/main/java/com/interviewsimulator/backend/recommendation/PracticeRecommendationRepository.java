package com.interviewsimulator.backend.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PracticeRecommendationRepository extends JpaRepository<PracticeRecommendationEntity, Long> {
    List<PracticeRecommendationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserIdAndStatus(Long userId, String status);
}
