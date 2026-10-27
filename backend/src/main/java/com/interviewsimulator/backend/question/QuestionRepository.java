package com.interviewsimulator.backend.question;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {
    List<QuestionEntity> findByActiveTrueOrderByIdAsc();
    Optional<QuestionEntity> findBySlug(String slug);
}
