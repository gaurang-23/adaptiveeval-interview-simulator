package com.interviewsimulator.backend.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.ai.AiInterviewerService;
import com.interviewsimulator.backend.ai.ExplanationEvaluationService;
import com.interviewsimulator.backend.ai.dto.ExplanationEvaluationResult;
import com.interviewsimulator.backend.ai.dto.InterviewerReplyResult;
import com.interviewsimulator.backend.common.*;
import com.interviewsimulator.backend.diagnosis.CompetencyDiagnosis;
import com.interviewsimulator.backend.diagnosis.MistakeDiagnosisEngine;
import com.interviewsimulator.backend.execution.RunnerExecutionResponse;
import com.interviewsimulator.backend.interview.AdaptiveQuestionSelectionEngine.SelectionDecision;
import com.interviewsimulator.backend.interview.dto.*;
import com.interviewsimulator.backend.question.QuestionEntity;
import com.interviewsimulator.backend.question.QuestionRepository;
import com.interviewsimulator.backend.question.QuestionService;
import com.interviewsimulator.backend.recommendation.RecommendationDto;
import com.interviewsimulator.backend.recommendation.RecommendationService;
import com.interviewsimulator.backend.skills.SkillAssessmentEntity;
import com.interviewsimulator.backend.skills.SkillAssessmentRepository;
import com.interviewsimulator.backend.skills.SkillUpdateEngine;
import com.interviewsimulator.backend.skills.UserSkillProfileEntity;
import com.interviewsimulator.backend.skills.UserSkillProfileRepository;
import com.interviewsimulator.backend.submission.*;
import com.interviewsimulator.backend.submission.SubmissionEvaluatorService.CodeStructuralSignals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InterviewOrchestrationService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewMessageRepository messageRepository;
    private final QuestionRepository questionRepository;
    private final QuestionService questionService;
    private final AttemptRepository attemptRepository;
    private final FeedbackRepository feedbackRepository;
    private final QuestionEventRepository eventRepository;
    private final UserSkillProfileRepository skillProfileRepository;
    private final SkillAssessmentRepository skillAssessmentRepository;
    private final SubmissionEvaluatorService submissionEvaluator;
    private final AiInterviewerService aiInterviewer;
    private final ExplanationEvaluationService explanationEvaluator;
    private final MistakeDiagnosisEngine diagnosisEngine;
    private final SkillUpdateEngine skillUpdateEngine;
    private final AdaptiveQuestionSelectionEngine selectionEngine;
    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper;

    public InterviewOrchestrationService(
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository interviewQuestionRepository,
            InterviewMessageRepository messageRepository,
            QuestionRepository questionRepository,
            QuestionService questionService,
            AttemptRepository attemptRepository,
            FeedbackRepository feedbackRepository,
            QuestionEventRepository eventRepository,
            UserSkillProfileRepository skillProfileRepository,
            SkillAssessmentRepository skillAssessmentRepository,
            SubmissionEvaluatorService submissionEvaluator,
            AiInterviewerService aiInterviewer,
            ExplanationEvaluationService explanationEvaluator,
            MistakeDiagnosisEngine diagnosisEngine,
            SkillUpdateEngine skillUpdateEngine,
            AdaptiveQuestionSelectionEngine selectionEngine,
            RecommendationService recommendationService,
            ObjectMapper objectMapper
    ) {
        this.sessionRepository = sessionRepository;
        this.interviewQuestionRepository = interviewQuestionRepository;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.questionService = questionService;
        this.attemptRepository = attemptRepository;
        this.feedbackRepository = feedbackRepository;
        this.eventRepository = eventRepository;
        this.skillProfileRepository = skillProfileRepository;
        this.skillAssessmentRepository = skillAssessmentRepository;
        this.submissionEvaluator = submissionEvaluator;
        this.aiInterviewer = aiInterviewer;
        this.explanationEvaluator = explanationEvaluator;
        this.diagnosisEngine = diagnosisEngine;
        this.skillUpdateEngine = skillUpdateEngine;
        this.selectionEngine = selectionEngine;
        this.recommendationService = recommendationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewSessionSummaryDto createInterview(Long userId, CreateInterviewRequest request) {
        int maxAllowedQuestions = switch (request.durationMinutes()) {
            case 15 -> 2;
            case 30 -> 3;
            default -> 4;
        };
        int constrainedQuestions = Math.max(1, Math.min(request.questionCount(), maxAllowedQuestions));

        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setUserId(userId);
        session.setInterviewType(request.interviewType().trim().toUpperCase());
        session.setConfiguredDifficulty(request.difficulty().trim().toUpperCase());
        session.setInterviewStyle(request.interviewStyle().trim().toUpperCase());
        session.setTopicsJson(writeJson(request.topics()));
        session.setQuestionCount(constrainedQuestions);
        session.setDurationSeconds(request.durationMinutes() * 60);
        session.setStatus("CREATED");
        session.setCreatedAt(Instant.now());

        InterviewSessionEntity saved = sessionRepository.save(session);
        return toSessionSummaryDto(saved);
    }

    @Transactional(readOnly = true)
    public List<InterviewSessionSummaryDto> listUserInterviews(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSessionSummaryDto)
                .toList();
    }

    @Transactional
    public InterviewWorkspaceStateDto startInterview(Long userId, Long sessionId) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            return getWorkspaceState(userId, sessionId);
        }

        if ("CREATED".equals(session.getStatus())) {
            Instant now = Instant.now();
            session.setStartedAt(now);
            session.setDeadlineAt(now.plusSeconds(session.getDurationSeconds()));
            session.setStatus("IN_PROGRESS");
            sessionRepository.save(session);

            assignNextQuestionInternal(session, 1, null, null, 0);
        }

        return getWorkspaceState(userId, sessionId);
    }

    @Transactional
    public InterviewWorkspaceStateDto getWorkspaceState(Long userId, Long sessionId) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        checkAndHandleExpiration(session);

        Optional<InterviewQuestionEntity> activeIqOpt = interviewQuestionRepository
                .findFirstBySessionIdAndStatusOrderBySequenceNumberDesc(sessionId, "IN_PROGRESS");

        if (activeIqOpt.isEmpty()) {
            List<InterviewQuestionEntity> allIqs = interviewQuestionRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
            if (!allIqs.isEmpty()) {
                activeIqOpt = Optional.of(allIqs.get(allIqs.size() - 1));
            }
        }

        ActiveInterviewQuestionDto activeDto = null;
        List<AttemptDto> currentAttempts = List.of();

        if (activeIqOpt.isPresent()) {
            InterviewQuestionEntity iq = activeIqOpt.get();
            QuestionEntity q = questionRepository.findById(iq.getQuestionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found."));
            activeDto = toActiveQuestionDto(iq, q, session.getQuestionCount());
            currentAttempts = attemptRepository
                    .findBySessionIdAndQuestionIdOrderBySubmittedAtAsc(sessionId, q.getId())
                    .stream()
                    .map(this::toAttemptDto)
                    .toList();
        }

        List<InterviewMessageDto> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(this::toMessageDto)
                .toList();

        long remainingSeconds = computeRemainingSeconds(session);

        return new InterviewWorkspaceStateDto(
                toSessionSummaryDto(session),
                remainingSeconds,
                Instant.now(),
                aiInterviewer.getActiveAiMode(),
                aiInterviewer.isLiveLlmAvailable(),
                activeDto,
                currentAttempts,
                messages
        );
    }

    @Transactional
    public ActiveInterviewQuestionDto getCurrentQuestion(Long userId, Long sessionId) {
        InterviewWorkspaceStateDto state = getWorkspaceState(userId, sessionId);
        if (state.activeQuestion() == null) {
            throw new BadRequestException("Interview has not been started yet.");
        }
        return state.activeQuestion();
    }

    @Transactional
    public AttemptDto submitCode(Long userId, Long sessionId, SubmitCodeRequest request) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        ensureSessionActive(session);

        InterviewQuestionEntity activeIq = getActiveInterviewQuestion(sessionId);
        QuestionEntity question = questionRepository.findById(activeIq.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found."));

        RunnerExecutionResponse execResponse = submissionEvaluator.evaluateCode(
                question,
                request.code(),
                request.sampleOnly()
        );

        AttemptEntity attempt = new AttemptEntity();
        attempt.setSessionId(sessionId);
        attempt.setQuestionId(question.getId());
        attempt.setSubmittedCode(request.code());
        attempt.setLanguage("JAVA");
        attempt.setFinalSubmission(!request.sampleOnly());
        attempt.setCompilationStatus(execResponse.compilationStatus());
        attempt.setCompilerOutput(execResponse.compilerOutput());
        attempt.setTestsPassed(execResponse.passedTests());
        attempt.setTestsTotal(execResponse.totalTests());
        attempt.setRuntimeMs(execResponse.runtimeMs());
        attempt.setMemoryKb(execResponse.memoryKb());
        attempt.setFailureCategory(execResponse.failureCategory());
        attempt.setTestResultsJson(writeJson(Map.of(
                "isolationModeUsed", execResponse.isolationModeUsed(),
                "testResults", execResponse.testResults() != null ? execResponse.testResults() : List.of()
        )));
        attempt.setSubmittedAt(Instant.now());

        AttemptEntity savedAttempt = attemptRepository.save(attempt);

        recordEvent(sessionId, question.getId(), request.sampleOnly() ? "CODE_RUN_SAMPLE" : "CODE_SUBMITTED", Map.of(
                "attemptId", savedAttempt.getId(),
                "compilationStatus", savedAttempt.getCompilationStatus(),
                "testsPassed", savedAttempt.getTestsPassed(),
                "testsTotal", savedAttempt.getTestsTotal(),
                "failureCategory", savedAttempt.getFailureCategory(),
                "runtimeMs", savedAttempt.getRuntimeMs()
        ));

        if (!request.sampleOnly()) {
            List<AttemptEntity> priorAttempts = attemptRepository
                    .findBySessionIdAndQuestionIdOrderBySubmittedAtAsc(sessionId, question.getId());
            InterviewerReplyResult followUp = aiInterviewer.generatePostSubmissionFollowUp(
                    question,
                    savedAttempt,
                    priorAttempts.size()
            );
            saveInterviewerMessage(sessionId, question.getId(), followUp);
        }

        return toAttemptDto(savedAttempt);
    }

    @Transactional
    public ExplanationEvaluationResult submitExplanation(Long userId, Long sessionId, SubmitExplanationRequest request) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        ensureSessionActive(session);

        InterviewQuestionEntity activeIq = getActiveInterviewQuestion(sessionId);
        QuestionEntity question = questionRepository.findById(activeIq.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found."));

        List<AttemptEntity> attempts = attemptRepository
                .findBySessionIdAndQuestionIdOrderBySubmittedAtAsc(sessionId, question.getId());
        String latestCode = attempts.isEmpty() ? question.getStarterCode() : attempts.get(attempts.size() - 1).getSubmittedCode();

        ExplanationEvaluationResult evalResult = explanationEvaluator.evaluateExplanation(
                question,
                latestCode,
                request.algorithmExplanation(),
                request.timeComplexity(),
                request.spaceComplexity(),
                request.tradeOffs()
        );

        activeIq.setExplanationSubmitted(true);
        interviewQuestionRepository.save(activeIq);

        recordEvent(sessionId, question.getId(), "EXPLANATION_SUBMITTED", Map.of(
                "algorithmExplanation", request.algorithmExplanation(),
                "timeComplexity", request.timeComplexity(),
                "spaceComplexity", request.spaceComplexity(),
                "tradeOffs", request.tradeOffs() != null ? request.tradeOffs() : "",
                "evaluation", evalResult
        ));

        // Save candidate's explanation summary and interviewer's rubric feedback in chat history
        InterviewMessageEntity candidateMsg = new InterviewMessageEntity();
        candidateMsg.setSessionId(sessionId);
        candidateMsg.setQuestionId(question.getId());
        candidateMsg.setRole("CANDIDATE");
        candidateMsg.setMessageType("EXPLANATION");
        candidateMsg.setContent(String.format(
                "**Submitted Explanation & Complexity Analysis**\n- **Claimed Time Complexity:** `%s`\n- **Claimed Space Complexity:** `%s`\n- **Approach:** %s\n- **Trade-offs:** %s",
                request.timeComplexity(),
                request.spaceComplexity(),
                request.algorithmExplanation(),
                request.tradeOffs() != null && !request.tradeOffs().isBlank() ? request.tradeOffs() : "N/A"
        ));
        messageRepository.save(candidateMsg);

        String fallbackNote = (!"LIVE_LLM".equals(evalResult.sourceType()) && evalResult.fallbackReason() != null && !evalResult.fallbackReason().isBlank())
                ? "\n- **Fallback Note:** " + evalResult.fallbackReason()
                : "";
        String feedbackText = String.format(
                "**Explanation Rubric Assessment (%s · Score: %.1f/100)**\n- **Evidence:** %s\n- **Expected Complexity:** Time `%s`, Space `%s`%s\n\n**Follow-up Question:** %s",
                "LIVE_LLM".equals(evalResult.sourceType()) ? "Live AI Evaluator" : "Deterministic Rubric Mode",
                evalResult.score(),
                evalResult.evidence(),
                evalResult.expectedTimeComplexity(),
                evalResult.expectedSpaceComplexity(),
                fallbackNote,
                evalResult.recommendedFollowUp()
        );
        saveInterviewerMessage(sessionId, question.getId(), new InterviewerReplyResult(
                feedbackText,
                "RUBRIC_FEEDBACK",
                "EVALUATION_COMPLETE",
                evalResult.sourceType(),
                evalResult.fallbackReason()
        ));

        return evalResult;
    }

    @Transactional
    public HintResponseDto requestProgressiveHint(Long userId, Long sessionId) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        ensureSessionActive(session);

        InterviewQuestionEntity activeIq = getActiveInterviewQuestion(sessionId);
        QuestionEntity question = questionRepository.findById(activeIq.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found."));

        List<String> allHints = questionService.parseStringList(question.getHintsJson());
        if (allHints.isEmpty()) {
            throw new BadRequestException("No hints are configured for this problem.");
        }

        int currentUsed = activeIq.getHintsUsed();
        int nextLevel = Math.min(allHints.size(), currentUsed + 1);
        if (currentUsed < allHints.size()) {
            activeIq.setHintsUsed(nextLevel);
            interviewQuestionRepository.save(activeIq);
        }

        String hintText = allHints.get(nextLevel - 1);
        List<String> unlocked = allHints.subList(0, nextLevel);
        Instant now = Instant.now();

        recordEvent(sessionId, question.getId(), "HINT_REQUESTED", Map.of(
                "hintLevel", nextLevel,
                "totalHints", allHints.size(),
                "hintText", hintText,
                "requestedAt", now.toString()
        ));

        saveInterviewerMessage(sessionId, question.getId(), new InterviewerReplyResult(
                String.format("**Progressive Hint (Level %d of %d):** %s", nextLevel, allHints.size(), hintText),
                "HINT",
                "HINT_PROVIDED",
                "DETERMINISTIC"
        ));

        return new HintResponseDto(nextLevel, allHints.size(), hintText, unlocked, now);
    }

    @Transactional
    public InterviewMessageDto postChatMessage(Long userId, Long sessionId, SendMessageRequest request) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        ensureSessionActive(session);

        InterviewQuestionEntity activeIq = getActiveInterviewQuestion(sessionId);
        QuestionEntity question = questionRepository.findById(activeIq.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found."));

        InterviewMessageEntity userMsg = new InterviewMessageEntity();
        userMsg.setSessionId(sessionId);
        userMsg.setQuestionId(question.getId());
        userMsg.setRole("CANDIDATE");
        userMsg.setMessageType("CHAT");
        userMsg.setContent(request.content().trim());
        userMsg.setMetadataJson(writeJson(Map.of("sourceType", "CANDIDATE")));
        messageRepository.save(userMsg);

        List<AttemptEntity> attempts = attemptRepository
                .findBySessionIdAndQuestionIdOrderBySubmittedAtAsc(sessionId, question.getId());
        AttemptEntity latestAttempt = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);

        InterviewerReplyResult reply = aiInterviewer.respondToCandidateMessage(
                question,
                request.content().trim(),
                request.currentCode(),
                activeIq.getHintsUsed(),
                latestAttempt
        );

        InterviewMessageEntity savedReply = saveInterviewerMessage(sessionId, question.getId(), reply);
        return toMessageDto(savedReply);
    }

    @Transactional
    public InterviewWorkspaceStateDto advanceToNextQuestion(Long userId, Long sessionId) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        if ("COMPLETED".equals(session.getStatus())) {
            return getWorkspaceState(userId, sessionId);
        }

        Optional<InterviewQuestionEntity> activeIqOpt = interviewQuestionRepository
                .findFirstBySessionIdAndStatusOrderBySequenceNumberDesc(sessionId, "IN_PROGRESS");

        if (activeIqOpt.isPresent()) {
            InterviewQuestionEntity activeIq = activeIqOpt.get();
            QuestionEntity currentQ = questionRepository.findById(activeIq.getQuestionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found."));

            Double passRatio = finalizeAndEvaluateQuestion(session, activeIq, currentQ);

            int nextSeq = activeIq.getSequenceNumber() + 1;
            long remainingSec = computeRemainingSeconds(session);

            if (nextSeq <= session.getQuestionCount() && remainingSec > 60) {
                assignNextQuestionInternal(
                        session,
                        nextSeq,
                        DifficultyLevel.fromString(currentQ.getDifficulty()),
                        passRatio,
                        activeIq.getHintsUsed()
                );
            } else {
                completeSessionInternal(session);
            }
        }

        return getWorkspaceState(userId, sessionId);
    }

    @Transactional
    public InterviewReportDto endInterview(Long userId, Long sessionId) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        if (!"COMPLETED".equals(session.getStatus())) {
            Optional<InterviewQuestionEntity> activeIqOpt = interviewQuestionRepository
                    .findFirstBySessionIdAndStatusOrderBySequenceNumberDesc(sessionId, "IN_PROGRESS");
            if (activeIqOpt.isPresent()) {
                InterviewQuestionEntity activeIq = activeIqOpt.get();
                QuestionEntity currentQ = questionRepository.findById(activeIq.getQuestionId()).orElse(null);
                if (currentQ != null) {
                    finalizeAndEvaluateQuestion(session, activeIq, currentQ);
                }
            }
            completeSessionInternal(session);
        }
        return getInterviewReport(userId, sessionId);
    }

    @Transactional
    public InterviewReportDto getInterviewReport(Long userId, Long sessionId) {
        InterviewSessionEntity session = getOwnedSession(userId, sessionId);
        if (!"COMPLETED".equals(session.getStatus())) {
            endInterview(userId, sessionId);
            session = getOwnedSession(userId, sessionId);
        }

        return buildReportFromPersistedData(session);
    }

    private Double finalizeAndEvaluateQuestion(
            InterviewSessionEntity session,
            InterviewQuestionEntity iq,
            QuestionEntity question
    ) {
        if ("COMPLETED".equals(iq.getStatus())) {
            return 1.0;
        }

        List<AttemptEntity> attempts = attemptRepository
                .findBySessionIdAndQuestionIdOrderBySubmittedAtAsc(session.getId(), question.getId());
        String latestCode = attempts.isEmpty() ? "" : attempts.get(attempts.size() - 1).getSubmittedCode();
        CodeStructuralSignals signals = submissionEvaluator.analyzeStructure(question, latestCode);

        ExplanationEvaluationResult explanationEval = extractLatestExplanationResult(session.getId(), question.getId());

        List<CompetencyDiagnosis> diagnoses = diagnosisEngine.diagnoseQuestionPerformance(
                question,
                attempts,
                signals,
                iq.getHintsUsed(),
                explanationEval
        );

        AttemptEntity latestAttempt = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);

        for (CompetencyDiagnosis d : diagnoses) {
            if (!d.sufficientEvidence()) continue;
            FeedbackEntity fb = new FeedbackEntity();
            fb.setAttemptId(latestAttempt != null ? latestAttempt.getId() : null);
            fb.setSessionId(session.getId());
            fb.setQuestionId(question.getId());
            fb.setCategory(d.competency().name());
            fb.setScore(d.score());
            fb.setConfidence(d.confidence());
            fb.setEvidence(writeJson(Map.of(
                    "diagnosisCode", d.diagnosisCode(),
                    "observedFacts", d.observedFacts(),
                    "inferredHypothesis", d.inferredHypothesis()
            )));
            fb.setRecommendation(d.recommendedIntervention());
            fb.setSourceType(d.sourceType());
            fb.setCreatedAt(Instant.now());
            feedbackRepository.save(fb);
        }

        skillUpdateEngine.applyDiagnosesAndUpdateSkills(
                session.getUserId(),
                session.getId(),
                question.getId(),
                DifficultyLevel.fromString(question.getDifficulty()),
                iq.getHintsUsed(),
                diagnoses
        );

        iq.setStatus("COMPLETED");
        iq.setCompletedAt(Instant.now());
        interviewQuestionRepository.save(iq);

        if (latestAttempt == null || latestAttempt.getTestsTotal() == 0) {
            return 0.0;
        }
        return (double) latestAttempt.getTestsPassed() / latestAttempt.getTestsTotal();
    }

    private void assignNextQuestionInternal(
            InterviewSessionEntity session,
            int sequenceNumber,
            DifficultyLevel previousDifficulty,
            Double previousPassRatio,
            int previousHintsUsed
    ) {
        List<QuestionEntity> allQuestions = questionRepository.findByActiveTrueOrderByIdAsc();
        List<UserSkillProfileEntity> userSkills = skillProfileRepository.findByUserIdOrderByCompetencyAsc(session.getUserId());
        List<InterviewQuestionEntity> existingIqs = interviewQuestionRepository.findBySessionIdOrderBySequenceNumberAsc(session.getId());

        Set<Long> assignedIds = existingIqs.stream()
                .map(InterviewQuestionEntity::getQuestionId)
                .collect(Collectors.toSet());

        Map<Long, QuestionEntity> questionMap = allQuestions.stream()
                .collect(Collectors.toMap(QuestionEntity::getId, q -> q));

        Set<String> coveredTopics = existingIqs.stream()
                .map(iq -> questionMap.get(iq.getQuestionId()))
                .filter(Objects::nonNull)
                .map(QuestionEntity::getTopic)
                .collect(Collectors.toSet());

        List<String> requestedTopics = parseStringList(session.getTopicsJson());
        long remainingSeconds = computeRemainingSeconds(session);

        SelectionDecision decision = selectionEngine.selectNextQuestion(
                allQuestions,
                userSkills,
                session.getInterviewType(),
                DifficultyLevel.fromString(session.getConfiguredDifficulty()),
                requestedTopics,
                assignedIds,
                coveredTopics,
                previousDifficulty,
                previousPassRatio,
                previousHintsUsed,
                remainingSeconds
        );

        InterviewQuestionEntity iq = new InterviewQuestionEntity();
        iq.setSessionId(session.getId());
        iq.setQuestionId(decision.question().getId());
        iq.setSequenceNumber(sequenceNumber);
        iq.setSelectionReason(decision.selectionReason());
        iq.setTargetCompetency(decision.targetCompetency().name());
        iq.setStatus("IN_PROGRESS");
        iq.setHintsUsed(0);
        iq.setExplanationSubmitted(false);
        iq.setStartedAt(Instant.now());
        interviewQuestionRepository.save(iq);

        recordEvent(session.getId(), decision.question().getId(), "QUESTION_ASSIGNED", Map.of(
                "sequenceNumber", sequenceNumber,
                "targetCompetency", decision.targetCompetency().name(),
                "selectionReason", decision.selectionReason()
        ));

        InterviewerReplyResult intro = aiInterviewer.generateQuestionIntro(
                decision.question(),
                sequenceNumber,
                session.getQuestionCount(),
                decision.selectionReason(),
                session.getInterviewStyle()
        );
        saveInterviewerMessage(session.getId(), decision.question().getId(), intro);
    }

    private void completeSessionInternal(InterviewSessionEntity session) {
        session.setStatus("COMPLETED");
        session.setCompletedAt(Instant.now());

        List<SkillAssessmentEntity> sessionAssessments = skillAssessmentRepository
                .findBySessionIdOrderByAssessedAtAsc(session.getId());
        List<AttemptEntity> sessionAttempts = attemptRepository
                .findBySessionIdOrderBySubmittedAtAsc(session.getId());

        double overallScore = 0.0;
        if (!sessionAssessments.isEmpty()) {
            overallScore = sessionAssessments.stream()
                    .mapToDouble(SkillAssessmentEntity::getScore)
                    .average()
                    .orElse(0.0);
        } else if (!sessionAttempts.isEmpty()) {
            overallScore = sessionAttempts.stream()
                    .mapToDouble(a -> a.getTestsTotal() > 0 ? (a.getTestsPassed() * 100.0 / a.getTestsTotal()) : 0.0)
                    .max()
                    .orElse(0.0);
        }
        session.setOverallScore(Math.round(overallScore * 10.0) / 10.0);
        sessionRepository.save(session);

        recommendationService.refreshRecommendationsForUser(session.getUserId());

        InterviewReportDto report = buildReportFromPersistedData(session);
        session.setReportJson(writeJson(report));
        sessionRepository.save(session);
    }

    private InterviewReportDto buildReportFromPersistedData(InterviewSessionEntity session) {
        Long userId = session.getUserId();
        Long sessionId = session.getId();

        List<InterviewQuestionEntity> iqs = interviewQuestionRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId);
        List<AttemptEntity> allAttempts = attemptRepository.findBySessionIdOrderBySubmittedAtAsc(sessionId);
        List<FeedbackEntity> allFeedback = feedbackRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<SkillAssessmentEntity> sessionAssessments = skillAssessmentRepository.findBySessionIdOrderByAssessedAtAsc(sessionId);
        List<UserSkillProfileEntity> currentProfiles = skillProfileRepository.findByUserIdOrderByCompetencyAsc(userId);

        Map<Long, QuestionEntity> questionMap = questionRepository.findAll().stream()
                .collect(Collectors.toMap(QuestionEntity::getId, q -> q));

        // 1. Previous session comparison
        List<InterviewSessionEntity> completedSessions = sessionRepository
                .findByUserIdAndStatusOrderByCompletedAtDesc(userId, "COMPLETED");
        InterviewReportDto.PreviousSessionComparison prevComp = null;
        for (InterviewSessionEntity s : completedSessions) {
            if (!s.getId().equals(sessionId) && s.getOverallScore() != null
                    && s.getCompletedAt() != null && session.getCompletedAt() != null
                    && !s.getCompletedAt().isAfter(session.getCompletedAt())) {
                double currScore = session.getOverallScore() != null ? session.getOverallScore() : 0.0;
                double delta = Math.round((currScore - s.getOverallScore()) * 10.0) / 10.0;
                prevComp = new InterviewReportDto.PreviousSessionComparison(
                        s.getId(),
                        s.getOverallScore(),
                        delta,
                        s.getCompletedAt()
                );
                break;
            }
        }

        // 2. Competency breakdown
        Map<String, SkillAssessmentEntity> latestSessionAssessmentByComp = new HashMap<>();
        Map<String, SkillAssessmentEntity> firstSessionAssessmentByComp = new HashMap<>();
        for (SkillAssessmentEntity sa : sessionAssessments) {
            firstSessionAssessmentByComp.putIfAbsent(sa.getCompetency(), sa);
            latestSessionAssessmentByComp.put(sa.getCompetency(), sa);
        }

        List<InterviewReportDto.CompetencyBreakdownItem> compItems = new ArrayList<>();
        for (CompetencyType comp : CompetencyType.values()) {
            SkillAssessmentEntity latestSa = latestSessionAssessmentByComp.get(comp.name());
            SkillAssessmentEntity firstSa = firstSessionAssessmentByComp.get(comp.name());
            UserSkillProfileEntity profile = currentProfiles.stream()
                    .filter(p -> p.getCompetency().equals(comp.name()))
                    .findFirst()
                    .orElse(null);

            if (latestSa != null) {
                Double prev = firstSa != null ? firstSa.getPreviousScore() : null;
                Double delta = prev != null ? Math.round((latestSa.getScore() - prev) * 10.0) / 10.0 : null;
                String sourceType = extractFieldFromJson(latestSa.getEvidenceJson(), "sourceType", "DETERMINISTIC");
                compItems.add(new InterviewReportDto.CompetencyBreakdownItem(
                        comp.name(),
                        comp.getLabel(),
                        latestSa.getScore(),
                        prev,
                        delta,
                        latestSa.getConfidence(),
                        true,
                        sourceType
                ));
            } else {
                boolean hasHistorical = profile != null && profile.getAssessmentsCount() > 0;
                compItems.add(new InterviewReportDto.CompetencyBreakdownItem(
                        comp.name(),
                        comp.getLabel(),
                        hasHistorical ? profile.getScore() : 50.0,
                        null,
                        null,
                        hasHistorical ? profile.getConfidence() : 0.0,
                        hasHistorical,
                        "INSUFFICIENT_SESSION_EVIDENCE"
                ));
            }
        }

        List<InterviewReportDto.CompetencyBreakdownItem> assessedItems = compItems.stream()
                .filter(InterviewReportDto.CompetencyBreakdownItem::sufficientEvidence)
                .sorted(Comparator.comparingDouble(InterviewReportDto.CompetencyBreakdownItem::score).reversed())
                .toList();

        List<String> strongest = assessedItems.stream()
                .filter(i -> i.score() >= 65.0)
                .limit(2)
                .map(InterviewReportDto.CompetencyBreakdownItem::label)
                .toList();

        List<String> weakest = assessedItems.stream()
                .sorted(Comparator.comparingDouble(InterviewReportDto.CompetencyBreakdownItem::score))
                .limit(2)
                .map(InterviewReportDto.CompetencyBreakdownItem::label)
                .toList();

        // 3. Question attempt reports & Topic breakdown
        List<InterviewReportDto.QuestionAttemptReportItem> questionReports = new ArrayList<>();
        Map<String, List<InterviewReportDto.QuestionAttemptReportItem>> byTopic = new LinkedHashMap<>();

        for (InterviewQuestionEntity iq : iqs) {
            QuestionEntity q = questionMap.get(iq.getQuestionId());
            if (q == null) continue;
            List<AttemptEntity> qAttempts = allAttempts.stream()
                    .filter(a -> a.getQuestionId().equals(q.getId()))
                    .toList();
            AttemptEntity lastAttempt = qAttempts.isEmpty() ? null : qAttempts.get(qAttempts.size() - 1);
            long bestRuntime = qAttempts.stream()
                    .filter(a -> "SUCCESS".equals(a.getCompilationStatus()) && a.getRuntimeMs() > 0)
                    .mapToLong(AttemptEntity::getRuntimeMs)
                    .min()
                    .orElse(lastAttempt != null ? lastAttempt.getRuntimeMs() : 0L);

            var item = new InterviewReportDto.QuestionAttemptReportItem(
                    q.getId(),
                    iq.getSequenceNumber(),
                    q.getTitle(),
                    q.getTopic(),
                    q.getDifficulty(),
                    iq.getSelectionReason(),
                    iq.getTargetCompetency(),
                    qAttempts.size(),
                    lastAttempt != null ? lastAttempt.getCompilationStatus() : "NOT_SUBMITTED",
                    lastAttempt != null ? lastAttempt.getTestsPassed() : 0,
                    lastAttempt != null ? lastAttempt.getTestsTotal() : 0,
                    bestRuntime,
                    q.getExpectedTimeComplexity(),
                    q.getExpectedSpaceComplexity(),
                    iq.getHintsUsed(),
                    Boolean.TRUE.equals(iq.getExplanationSubmitted())
            );
            questionReports.add(item);
            byTopic.computeIfAbsent(q.getTopic(), k -> new ArrayList<>()).add(item);
        }

        List<InterviewReportDto.TopicBreakdownItem> topicResults = new ArrayList<>();
        for (var entry : byTopic.entrySet()) {
            List<InterviewReportDto.QuestionAttemptReportItem> items = entry.getValue();
            int passed = items.stream().mapToInt(InterviewReportDto.QuestionAttemptReportItem::testsPassed).sum();
            int total = items.stream().mapToInt(InterviewReportDto.QuestionAttemptReportItem::testsTotal).sum();
            double avg = total > 0 ? Math.round((passed * 1000.0 / total)) / 10.0 : 0.0;
            topicResults.add(new InterviewReportDto.TopicBreakdownItem(
                    entry.getKey(),
                    items.size(),
                    passed,
                    total,
                    avg
            ));
        }

        // 4. Diagnosed Mistakes & Evidence
        List<InterviewReportDto.DiagnosedMistakeItem> diagnosedMistakes = new ArrayList<>();
        for (FeedbackEntity fb : allFeedback) {
            QuestionEntity q = questionMap.get(fb.getQuestionId());
            String qTitle = q != null ? q.getTitle() : "Question #" + fb.getQuestionId();
            String compLabel = fb.getCategory();
            try {
                compLabel = CompetencyType.valueOf(fb.getCategory()).getLabel();
            } catch (IllegalArgumentException ignored) {}

            String diagCode = extractFieldFromJson(fb.getEvidence(), "diagnosisCode", fb.getCategory());
            String hypothesis = extractFieldFromJson(fb.getEvidence(), "inferredHypothesis", fb.getEvidence());
            List<String> facts = extractListFromJson(fb.getEvidence(), "observedFacts");

            diagnosedMistakes.add(new InterviewReportDto.DiagnosedMistakeItem(
                    fb.getQuestionId(),
                    qTitle,
                    fb.getCategory(),
                    compLabel,
                    diagCode,
                    Math.round(fb.getScore() * 10.0) / 10.0,
                    Math.round(fb.getConfidence() * 100.0) / 100.0,
                    facts,
                    hypothesis,
                    fb.getRecommendation(),
                    fb.getSourceType()
            ));
        }

        List<RecommendationDto> recommendations = recommendationService.getOrGenerateRecommendations(userId);

        return new InterviewReportDto(
                session.getId(),
                session.getInterviewType(),
                session.getConfiguredDifficulty(),
                session.getInterviewStyle(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getStatus(),
                session.getOverallScore() != null ? session.getOverallScore() : 0.0,
                aiInterviewer.getActiveAiMode(),
                prevComp,
                compItems,
                strongest,
                weakest,
                topicResults,
                questionReports,
                diagnosedMistakes,
                recommendations
        );
    }

    private ExplanationEvaluationResult extractLatestExplanationResult(Long sessionId, Long questionId) {
        List<QuestionEventEntity> events = eventRepository
                .findBySessionIdAndQuestionIdOrderByCreatedAtAsc(sessionId, questionId);
        for (int i = events.size() - 1; i >= 0; i--) {
            QuestionEventEntity ev = events.get(i);
            if ("EXPLANATION_SUBMITTED".equals(ev.getEventType())) {
                try {
                    JsonNode root = objectMapper.readTree(ev.getEventPayloadJson());
                    JsonNode evalNode = root.path("evaluation");
                    if (!evalNode.isMissingNode()) {
                        return objectMapper.treeToValue(evalNode, ExplanationEvaluationResult.class);
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private InterviewSessionEntity getOwnedSession(Long userId, Long sessionId) {
        InterviewSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found with id: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("You do not have permission to access this interview session.");
        }
        return session;
    }

    private void checkAndHandleExpiration(InterviewSessionEntity session) {
        if ("IN_PROGRESS".equals(session.getStatus()) && session.getDeadlineAt() != null) {
            if (Instant.now().isAfter(session.getDeadlineAt())) {
                Optional<InterviewQuestionEntity> activeIqOpt = interviewQuestionRepository
                        .findFirstBySessionIdAndStatusOrderBySequenceNumberDesc(session.getId(), "IN_PROGRESS");
                if (activeIqOpt.isPresent()) {
                    QuestionEntity q = questionRepository.findById(activeIqOpt.get().getQuestionId()).orElse(null);
                    if (q != null) {
                        finalizeAndEvaluateQuestion(session, activeIqOpt.get(), q);
                    }
                }
                completeSessionInternal(session);
            }
        }
    }

    private void ensureSessionActive(InterviewSessionEntity session) {
        checkAndHandleExpiration(session);
        if ("COMPLETED".equals(session.getStatus())) {
            throw new BadRequestException("This interview session has already ended or expired.");
        }
        if ("CREATED".equals(session.getStatus())) {
            throw new BadRequestException("Start the interview session before performing this action.");
        }
    }

    private InterviewQuestionEntity getActiveInterviewQuestion(Long sessionId) {
        return interviewQuestionRepository
                .findFirstBySessionIdAndStatusOrderBySequenceNumberDesc(sessionId, "IN_PROGRESS")
                .orElseThrow(() -> new BadRequestException("No active question found for this session."));
    }

    private long computeRemainingSeconds(InterviewSessionEntity session) {
        if (!"IN_PROGRESS".equals(session.getStatus()) || session.getDeadlineAt() == null) {
            return "CREATED".equals(session.getStatus()) ? session.getDurationSeconds() : 0L;
        }
        long diff = Duration.between(Instant.now(), session.getDeadlineAt()).getSeconds();
        return Math.max(0L, diff);
    }

    private void recordEvent(Long sessionId, Long questionId, String eventType, Map<String, Object> payload) {
        QuestionEventEntity ev = new QuestionEventEntity();
        ev.setSessionId(sessionId);
        ev.setQuestionId(questionId);
        ev.setEventType(eventType);
        ev.setEventPayloadJson(writeJson(payload));
        ev.setCreatedAt(Instant.now());
        eventRepository.save(ev);
    }

    private InterviewMessageEntity saveInterviewerMessage(
            Long sessionId,
            Long questionId,
            InterviewerReplyResult reply
    ) {
        InterviewMessageEntity msg = new InterviewMessageEntity();
        msg.setSessionId(sessionId);
        msg.setQuestionId(questionId);
        msg.setRole("INTERVIEWER");
        msg.setMessageType(reply.messageType());
        msg.setContent(reply.replyContent());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", reply.stage());
        metadata.put("sourceType", reply.sourceType());
        if (reply.fallbackReason() != null && !reply.fallbackReason().isBlank()) {
            metadata.put("fallbackReason", reply.fallbackReason());
        }
        msg.setMetadataJson(writeJson(metadata));
        msg.setCreatedAt(Instant.now());
        return messageRepository.save(msg);
    }

    private InterviewSessionSummaryDto toSessionSummaryDto(InterviewSessionEntity s) {
        return new InterviewSessionSummaryDto(
                s.getId(),
                s.getInterviewType(),
                s.getConfiguredDifficulty(),
                s.getInterviewStyle(),
                parseStringList(s.getTopicsJson()),
                s.getQuestionCount(),
                s.getDurationSeconds(),
                s.getStartedAt(),
                s.getDeadlineAt(),
                s.getCompletedAt(),
                s.getStatus(),
                s.getOverallScore(),
                s.getCreatedAt()
        );
    }

    private ActiveInterviewQuestionDto toActiveQuestionDto(
            InterviewQuestionEntity iq,
            QuestionEntity q,
            int totalQuestions
    ) {
        List<String> allHints = questionService.parseStringList(q.getHintsJson());
        int used = Math.min(allHints.size(), iq.getHintsUsed() != null ? iq.getHintsUsed() : 0);
        List<String> unlocked = allHints.subList(0, used);

        return new ActiveInterviewQuestionDto(
                iq.getId(),
                iq.getSequenceNumber(),
                totalQuestions,
                iq.getSelectionReason(),
                iq.getTargetCompetency(),
                iq.getStatus(),
                used,
                unlocked,
                Boolean.TRUE.equals(iq.getExplanationSubmitted()),
                iq.getStartedAt(),
                questionService.toDetailDto(q)
        );
    }

    private AttemptDto toAttemptDto(AttemptEntity a) {
        String isolationMode = "RESTRICTED_LOCAL_SANDBOX";
        List<RunnerExecutionResponse.RunnerTestCaseResult> testResults = List.of();
        if (a.getTestResultsJson() != null && !a.getTestResultsJson().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(a.getTestResultsJson());
                isolationMode = node.path("isolationModeUsed").asText("RESTRICTED_LOCAL_SANDBOX");
                JsonNode trNode = node.path("testResults");
                if (trNode.isArray()) {
                    testResults = objectMapper.convertValue(trNode, new TypeReference<>() {});
                }
            } catch (Exception ignored) {}
        }
        return new AttemptDto(
                a.getId(),
                a.getQuestionId(),
                a.getSubmittedCode(),
                a.getLanguage(),
                Boolean.TRUE.equals(a.getFinalSubmission()),
                a.getCompilationStatus(),
                a.getCompilerOutput(),
                a.getTestsPassed(),
                a.getTestsTotal(),
                a.getRuntimeMs(),
                a.getMemoryKb(),
                a.getFailureCategory(),
                isolationMode,
                testResults,
                a.getSubmittedAt()
        );
    }

    private InterviewMessageDto toMessageDto(InterviewMessageEntity m) {
        String sourceType = extractFieldFromJson(m.getMetadataJson(), "sourceType", "DETERMINISTIC");
        return new InterviewMessageDto(
                m.getId(),
                m.getQuestionId(),
                m.getRole(),
                m.getContent(),
                m.getMessageType(),
                sourceType,
                m.getCreatedAt()
        );
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String extractFieldFromJson(String json, String field, String defaultValue) {
        if (json == null || json.isBlank()) return defaultValue;
        try {
            JsonNode node = objectMapper.readTree(json);
            String val = node.path(field).asText("");
            return val.isBlank() ? defaultValue : val;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private List<String> extractListFromJson(String json, String field) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json).path(field);
            if (node.isArray()) {
                List<String> out = new ArrayList<>();
                for (JsonNode item : node) out.add(item.asText());
                return out;
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
