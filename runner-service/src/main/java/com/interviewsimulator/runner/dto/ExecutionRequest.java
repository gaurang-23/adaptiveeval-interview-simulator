package com.interviewsimulator.runner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ExecutionRequest(
        @NotBlank String sourceCode,
        String language,
        String entryMethodName,
        @NotEmpty List<TestCaseSpec> testCases
) {}
