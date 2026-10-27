package com.interviewsimulator.backend.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionEventRepository extends JpaRepository<QuestionEventEntity, Long> {
    List<QuestionEventEntity> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    List<QuestionEventEntity> findBySessionIdAndQuestionIdOrderByCreatedAtAsc(Long sessionId, Long questionId);
}
