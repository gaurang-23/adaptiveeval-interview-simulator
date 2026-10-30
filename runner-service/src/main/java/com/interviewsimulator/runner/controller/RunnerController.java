package com.interviewsimulator.runner.controller;

import com.interviewsimulator.runner.dto.ExecutionRequest;
import com.interviewsimulator.runner.dto.ExecutionResponse;
import com.interviewsimulator.runner.service.JavaSandboxExecutor;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    return ResponseEntity.ok(
      Map.of(
        "status",
        "UP",
        "service",
        "isolated-java-runner",
        "effectiveIsolationMode",
        sandboxExecutor.getEffectiveIsolationMode(),
        "dockerAvailable",
        sandboxExecutor.isDockerAvailable()
      )
    );
  }

  @PostMapping("/execute")
  public ResponseEntity<ExecutionResponse> execute(
    @RequestHeader(
      value = "X-Runner-Secret",
      required = false
    ) String secretHeader,
    @Valid @RequestBody ExecutionRequest request
  ) {
    System.out.println("=== RUNNER EXECUTE REQUEST RECEIVED ===");
    System.out.println("Language: " + request.language());
    System.out.println("Test cases: " + request.testCases().size());

    if (
      sharedSecret != null &&
      !sharedSecret.isBlank() &&
      !sharedSecret.equals(secretHeader)
    ) {
      System.out.println("=== RUNNER SECRET MISMATCH ===");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    ExecutionResponse response = sandboxExecutor.execute(request);

    System.out.println("=== RUNNER EXECUTION FINISHED ===");
    System.out.println("Compilation status: " + response.compilationStatus());
    System.out.println("Compiler output: " + response.compilerOutput());

    return ResponseEntity.ok(response);
  }
}
