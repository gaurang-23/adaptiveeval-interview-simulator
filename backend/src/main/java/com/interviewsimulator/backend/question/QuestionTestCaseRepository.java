package com.interviewsimulator.backend.question;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionTestCaseRepository extends JpaRepository<QuestionTestCaseEntity, Long> {
    List<QuestionTestCaseEntity> findByQuestionIdOrderByOrderIndexAsc(Long questionId);
    List<QuestionTestCaseEntity> findByQuestionIdAndSampleTrueOrderByOrderIndexAsc(Long questionId);
}
