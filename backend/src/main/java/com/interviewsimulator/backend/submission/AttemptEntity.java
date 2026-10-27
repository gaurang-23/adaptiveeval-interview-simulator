package com.interviewsimulator.backend.submission;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attempts")
public class AttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "submitted_code", nullable = false, columnDefinition = "TEXT")
    private String submittedCode;

    @Column(nullable = false, length = 30)
    private String language = "JAVA";

    @Column(name = "is_final_submission", nullable = false)
    private Boolean finalSubmission = false;

    @Column(name = "compilation_status", nullable = false, length = 40)
    private String compilationStatus;

    @Column(name = "compiler_output", columnDefinition = "TEXT")
    private String compilerOutput;

    @Column(name = "tests_passed", nullable = false)
    private Integer testsPassed = 0;

    @Column(name = "tests_total", nullable = false)
    private Integer testsTotal = 0;

    @Column(name = "runtime_ms", nullable = false)
    private Long runtimeMs = 0L;

    @Column(name = "memory_kb", nullable = false)
    private Long memoryKb = 0L;

    @Column(name = "failure_category", nullable = false, length = 60)
    private String failureCategory = "NONE";

    @Column(name = "test_results_json", columnDefinition = "JSON")
    private String testResultsJson;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getSubmittedCode() { return submittedCode; }
    public void setSubmittedCode(String submittedCode) { this.submittedCode = submittedCode; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Boolean getFinalSubmission() { return finalSubmission; }
    public void setFinalSubmission(Boolean finalSubmission) { this.finalSubmission = finalSubmission; }

    public String getCompilationStatus() { return compilationStatus; }
    public void setCompilationStatus(String compilationStatus) { this.compilationStatus = compilationStatus; }

    public String getCompilerOutput() { return compilerOutput; }
    public void setCompilerOutput(String compilerOutput) { this.compilerOutput = compilerOutput; }

    public Integer getTestsPassed() { return testsPassed; }
    public void setTestsPassed(Integer testsPassed) { this.testsPassed = testsPassed; }

    public Integer getTestsTotal() { return testsTotal; }
    public void setTestsTotal(Integer testsTotal) { this.testsTotal = testsTotal; }

    public Long getRuntimeMs() { return runtimeMs; }
    public void setRuntimeMs(Long runtimeMs) { this.runtimeMs = runtimeMs; }

    public Long getMemoryKb() { return memoryKb; }
    public void setMemoryKb(Long memoryKb) { this.memoryKb = memoryKb; }

    public String getFailureCategory() { return failureCategory; }
    public void setFailureCategory(String failureCategory) { this.failureCategory = failureCategory; }

    public String getTestResultsJson() { return testResultsJson; }
    public void setTestResultsJson(String testResultsJson) { this.testResultsJson = testResultsJson; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
