package com.interviewsimulator.backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestionEntity, Long> {
    List<InterviewQuestionEntity> findBySessionIdOrderBySequenceNumberAsc(Long sessionId);
    Optional<InterviewQuestionEntity> findFirstBySessionIdAndStatusOrderBySequenceNumberDesc(Long sessionId, String status);
}
