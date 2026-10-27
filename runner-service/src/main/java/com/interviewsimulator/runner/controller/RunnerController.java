package com.interviewsimulator.runner.controller;

import com.interviewsimulator.runner.dto.ExecutionRequest;
import com.interviewsimulator.runner.dto.ExecutionResponse;
import com.interviewsimulator.runner.service.JavaSandboxExecutor;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/runner")
public class RunnerController {

    private final JavaSandboxExecutor sandboxExecutor;

    @Value("${runner.shared-secret:internal-runner-secret-key-2026}")
    private String sharedSecret;

    public RunnerController(JavaSandboxExecutor sandboxExecutor) {
        this.sandboxExecutor = sandboxExecutor;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "isolated-java-runner",
                "effectiveIsolationMode", sandboxExecutor.getEffectiveIsolationMode(),
                "dockerAvailable", sandboxExecutor.isDockerAvailable()
        ));
    }

    @PostMapping("/execute")
    public ResponseEntity<ExecutionResponse> execute(
            @RequestHeader(value = "X-Runner-Secret", required = false) String secretHeader,
            @Valid @RequestBody ExecutionRequest request
    ) {
        if (sharedSecret != null && !sharedSecret.isBlank() && !sharedSecret.equals(secretHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(sandboxExecutor.execute(request));
    }
}
