package com.interviewsimulator.backend.question;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "questions")
public class QuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(nullable = false, length = 30)
    private String difficulty;

    @Column(name = "question_type", nullable = false, length = 40)
    private String questionType = "DSA";

    @Column(name = "entry_method_name", nullable = false, length = 100)
    private String entryMethodName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(name = "constraints_text", nullable = false, columnDefinition = "TEXT")
    private String constraintsText;

    @Column(name = "examples_json", nullable = false, columnDefinition = "JSON")
    private String examplesJson;

    @Column(name = "starter_code", nullable = false, columnDefinition = "TEXT")
    private String starterCode;

    @Column(name = "reference_solution", nullable = false, columnDefinition = "TEXT")
    private String referenceSolution;

    @Column(name = "rubric_json", nullable = false, columnDefinition = "JSON")
    private String rubricJson;

    @Column(name = "expected_concepts_json", nullable = false, columnDefinition = "JSON")
    private String expectedConceptsJson;

    @Column(name = "hints_json", nullable = false, columnDefinition = "JSON")
    private String hintsJson;

    @Column(name = "expected_time_complexity", nullable = false, length = 60)
    private String expectedTimeComplexity;

    @Column(name = "expected_space_complexity", nullable = false, length = 60)
    private String expectedSpaceComplexity;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getEntryMethodName() { return entryMethodName; }
    public void setEntryMethodName(String entryMethodName) { this.entryMethodName = entryMethodName; }

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public String getConstraintsText() { return constraintsText; }
    public void setConstraintsText(String constraintsText) { this.constraintsText = constraintsText; }

    public String getExamplesJson() { return examplesJson; }
    public void setExamplesJson(String examplesJson) { this.examplesJson = examplesJson; }

    public String getStarterCode() { return starterCode; }
    public void setStarterCode(String starterCode) { this.starterCode = starterCode; }

    public String getReferenceSolution() { return referenceSolution; }
    public void setReferenceSolution(String referenceSolution) { this.referenceSolution = referenceSolution; }

    public String getRubricJson() { return rubricJson; }
    public void setRubricJson(String rubricJson) { this.rubricJson = rubricJson; }

    public String getExpectedConceptsJson() { return expectedConceptsJson; }
    public void setExpectedConceptsJson(String expectedConceptsJson) { this.expectedConceptsJson = expectedConceptsJson; }

    public String getHintsJson() { return hintsJson; }
    public void setHintsJson(String hintsJson) { this.hintsJson = hintsJson; }

    public String getExpectedTimeComplexity() { return expectedTimeComplexity; }
    public void setExpectedTimeComplexity(String expectedTimeComplexity) { this.expectedTimeComplexity = expectedTimeComplexity; }

    public String getExpectedSpaceComplexity() { return expectedSpaceComplexity; }
    public void setExpectedSpaceComplexity(String expectedSpaceComplexity) { this.expectedSpaceComplexity = expectedSpaceComplexity; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
