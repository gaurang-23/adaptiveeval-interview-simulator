package com.interviewsimulator.backend.execution;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CodeExecutionClient {

  private static final Logger log = LoggerFactory.getLogger(
    CodeExecutionClient.class
  );

  private final RestClient restClient;
  private final String sharedSecret;
  private final String runnerUrl;

  public CodeExecutionClient(
    @Value("${app.runner.url:http://127.0.0.1:8085}") String runnerUrl,
    @Value(
      "${app.runner.shared-secret:internal-runner-secret-key-2026}"
    ) String sharedSecret,
    @Value("${app.runner.timeout-ms:60000}") int timeoutMs
  ) {
    this.runnerUrl = runnerUrl;
    this.sharedSecret = sharedSecret;
    SimpleClientHttpRequestFactory factory =
      new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(10000));
    factory.setReadTimeout(Duration.ofMillis(timeoutMs));
    this.restClient = RestClient.builder()
      .baseUrl(runnerUrl)
      .requestFactory(factory)
      .build();
  }

  public RunnerExecutionResponse executeJavaSubmission(
    RunnerExecutionRequest request
  ) {
    try {
      RunnerExecutionResponse response = restClient
        .post()
        .uri("/api/runner/execute")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Runner-Secret", sharedSecret)
        .body(request)
        .retrieve()
        .body(RunnerExecutionResponse.class);
      if (response != null) {
        return response;
      }
    } catch (Exception e) {
      log.warn(
        "Runner request failed at {}. Exception type: {}",
        runnerUrl,
        e.getClass().getName(),
        e
      );
      return new RunnerExecutionResponse(
        "RUNNER_UNAVAILABLE",
        "Isolated Java Runner Service at " +
          runnerUrl +
          " is unreachable: " +
          e.getMessage(),
        request.testCases().size(),
        0,
        request.testCases().size(),
        0L,
        0L,
        "RUNNER_UNAVAILABLE",
        "Isolated Java Runner Service is unavailable. Start runner-service on port 8085.",
        "UNAVAILABLE",
        List.of()
      );
    }
    return new RunnerExecutionResponse(
      "RUNNER_UNAVAILABLE",
      "Empty response from runner service.",
      request.testCases().size(),
      0,
      request.testCases().size(),
      0L,
      0L,
      "RUNNER_UNAVAILABLE",
      "Empty response from runner service.",
      "UNAVAILABLE",
      List.of()
    );
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> checkRunnerHealth() {
    try {
      Map<String, Object> res = restClient
        .get()
        .uri("/api/runner/health")
        .retrieve()
        .body(Map.class);
      return res != null ? res : Map.of("status", "DOWN");
    } catch (Exception e) {
      return Map.of(
        "status",
        "DOWN",
        "error",
        e.getMessage() != null ? e.getMessage() : "Unreachable"
      );
    }
  }
}
