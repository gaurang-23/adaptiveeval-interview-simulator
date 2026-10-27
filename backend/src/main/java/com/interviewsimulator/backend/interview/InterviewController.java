package com.interviewsimulator.backend.interview;

import com.interviewsimulator.backend.ai.dto.ExplanationEvaluationResult;
import com.interviewsimulator.backend.auth.SecurityUtils;
import com.interviewsimulator.backend.interview.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewOrchestrationService orchestrationService;

    public InterviewController(InterviewOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping
    public ResponseEntity<InterviewSessionSummaryDto> createInterview(
            @Valid @RequestBody CreateInterviewRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(orchestrationService.createInterview(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<InterviewSessionSummaryDto>> listInterviews() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.listUserInterviews(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewWorkspaceStateDto> getInterview(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.getWorkspaceState(userId, id));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<InterviewWorkspaceStateDto> startInterview(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.startInterview(userId, id));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<InterviewReportDto> endInterview(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.endInterview(userId, id));
    }

    @PostMapping("/{id}/next-question")
    public ResponseEntity<InterviewWorkspaceStateDto> advanceToNextQuestion(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.advanceToNextQuestion(userId, id));
    }

    @GetMapping("/{id}/question")
    public ResponseEntity<ActiveInterviewQuestionDto> getCurrentQuestion(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.getCurrentQuestion(userId, id));
    }

    @PostMapping("/{id}/submissions")
    public ResponseEntity<AttemptDto> submitCode(
            @PathVariable Long id,
            @Valid @RequestBody SubmitCodeRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.submitCode(userId, id, request));
    }

    @PostMapping("/{id}/explanations")
    public ResponseEntity<ExplanationEvaluationResult> submitExplanation(
            @PathVariable Long id,
            @Valid @RequestBody SubmitExplanationRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.submitExplanation(userId, id, request));
    }

    @PostMapping("/{id}/hints")
    public ResponseEntity<HintResponseDto> requestHint(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.requestProgressiveHint(userId, id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<InterviewMessageDto> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.postChatMessage(userId, id, request));
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<InterviewReportDto> getReport(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orchestrationService.getInterviewReport(userId, id));
    }
}
