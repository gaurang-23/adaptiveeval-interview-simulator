package com.interviewsimulator.backend.question;

import com.interviewsimulator.backend.question.dto.QuestionDetailDto;
import com.interviewsimulator.backend.question.dto.QuestionSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionSummaryDto>> listQuestions(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String questionType
    ) {
        return ResponseEntity.ok(questionService.listActiveQuestions(topic, difficulty, questionType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionDetailDto> getQuestion(@PathVariable Long id) {
        return ResponseEntity.ok(questionService.getQuestionDetail(id));
    }
}
