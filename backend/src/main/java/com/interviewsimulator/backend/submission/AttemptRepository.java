package com.interviewsimulator.backend.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttemptRepository extends JpaRepository<AttemptEntity, Long> {
    List<AttemptEntity> findBySessionIdOrderBySubmittedAtAsc(Long sessionId);
    List<AttemptEntity> findBySessionIdAndQuestionIdOrderBySubmittedAtAsc(Long sessionId, Long questionId);
}
