package com.interviewsimulator.backend.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {
    List<FeedbackEntity> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    List<FeedbackEntity> findBySessionIdAndQuestionIdOrderByCreatedAtAsc(Long sessionId, Long questionId);
}
