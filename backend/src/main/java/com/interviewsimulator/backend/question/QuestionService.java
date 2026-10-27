package com.interviewsimulator.backend.question;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.common.ResourceNotFoundException;
import com.interviewsimulator.backend.question.dto.QuestionDetailDto;
import com.interviewsimulator.backend.question.dto.QuestionExampleDto;
import com.interviewsimulator.backend.question.dto.QuestionSummaryDto;
import com.interviewsimulator.backend.question.dto.SampleTestCaseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionTestCaseRepository testCaseRepository;
    private final ObjectMapper objectMapper;

    public QuestionService(
            QuestionRepository questionRepository,
            QuestionTestCaseRepository testCaseRepository,
            ObjectMapper objectMapper
    ) {
        this.questionRepository = questionRepository;
        this.testCaseRepository = testCaseRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<QuestionSummaryDto> listActiveQuestions(String topic, String difficulty, String questionType) {
        return questionRepository.findByActiveTrueOrderByIdAsc().stream()
                .filter(q -> topic == null || topic.isBlank() || q.getTopic().equalsIgnoreCase(topic.trim()))
                .filter(q -> difficulty == null || difficulty.isBlank() || q.getDifficulty().equalsIgnoreCase(difficulty.trim()))
                .filter(q -> questionType == null || questionType.isBlank() || q.getQuestionType().equalsIgnoreCase(questionType.trim()))
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionDetailDto getQuestionDetail(Long id) {
        QuestionEntity q = questionRepository.findById(id)
                .filter(QuestionEntity::getActive)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + id));
        return toDetailDto(q);
    }

    public QuestionSummaryDto toSummaryDto(QuestionEntity q) {
        return new QuestionSummaryDto(
                q.getId(),
                q.getTitle(),
                q.getSlug(),
                q.getTopic(),
                q.getDifficulty(),
                q.getQuestionType(),
                parseStringList(q.getExpectedConceptsJson())
        );
    }

    public QuestionDetailDto toDetailDto(QuestionEntity q) {
        List<SampleTestCaseDto> samples = testCaseRepository
                .findByQuestionIdAndSampleTrueOrderByOrderIndexAsc(q.getId())
                .stream()
                .map(tc -> new SampleTestCaseDto(tc.getId(), tc.getInputData(), tc.getExpectedOutput(), tc.getOrderIndex()))
                .toList();

        List<String> hints = parseStringList(q.getHintsJson());
        List<QuestionExampleDto> examples = parseExamples(q.getExamplesJson());

        return new QuestionDetailDto(
                q.getId(),
                q.getTitle(),
                q.getSlug(),
                q.getTopic(),
                q.getDifficulty(),
                q.getQuestionType(),
                q.getEntryMethodName(),
                q.getStatement(),
                q.getConstraintsText(),
                examples,
                q.getStarterCode(),
                hints.size(),
                samples
        );
    }

    public List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<QuestionExampleDto> parseExamples(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
