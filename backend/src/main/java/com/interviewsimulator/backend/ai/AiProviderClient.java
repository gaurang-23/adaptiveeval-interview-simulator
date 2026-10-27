package com.interviewsimulator.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewsimulator.backend.ai.dto.AiConnectionTestResultDto;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class AiProviderClient {

  private static final Logger log = LoggerFactory.getLogger(
    AiProviderClient.class
  );

  private static final Set<String> PLACEHOLDER_KEYS = Set.of(
    "your_api_key_here",
    "your-api-key-here",
    "replace_with_your_api_key",
    "replace-with-your-api-key",
    "changeme",
    "placeholder"
  );

  private static final Duration HEALTH_CACHE_TTL = Duration.ofMinutes(5);

  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final String providerName;
  private final String apiKey;
  private final String baseUrl;
  private final String chatCompletionsUrl;
  private final String model;
  private final int timeoutMs;

  private volatile AiConnectionStatus cachedStatus;
  private volatile String lastErrorCategory;
  private volatile String lastMessage;
  private volatile String lastTroubleshootingRecommendation;
  private volatile Instant lastTestedAt;
  private volatile Long lastLatencyMs;

  @Autowired
  public AiProviderClient(
    ObjectMapper objectMapper,
    @Value("${app.ai.provider:OPENAI_COMPATIBLE}") String providerName,
    @Value("${app.ai.api-key:}") String apiKey,
    @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
    @Value("${app.ai.model:gpt-4o-mini}") String model,
    @Value("${app.ai.timeout-ms:12000}") int timeoutMs
  ) {
    this(
      objectMapper,
      providerName,
      apiKey,
      baseUrl,
      model,
      timeoutMs,
      buildDefaultRestClient(timeoutMs)
    );
  }

  public AiProviderClient(
    ObjectMapper objectMapper,
    String providerName,
    String apiKey,
    String baseUrl,
    String model,
    int timeoutMs,
    RestClient restClient
  ) {
    this.objectMapper = objectMapper;
    this.providerName = (providerName == null || providerName.isBlank())
      ? "OPENAI_COMPATIBLE"
      : providerName.trim();
    this.apiKey = sanitizeApiKey(apiKey);
    this.baseUrl = normalizeBaseUrl(this.providerName, baseUrl);
    this.chatCompletionsUrl = resolveChatCompletionsUrl(this.baseUrl);
    this.model = (model == null || model.isBlank())
      ? defaultModelForProvider(this.providerName)
      : model.trim();
    this.timeoutMs = Math.max(1000, timeoutMs);
    this.restClient =
      restClient != null ? restClient : buildDefaultRestClient(this.timeoutMs);

    if (isConfigured()) {
      this.cachedStatus = AiConnectionStatus.CONFIGURED_UNVERIFIED;
      this.lastMessage =
        "AI provider credentials are configured (" +
        this.providerName +
        " · " +
        this.model +
        "), awaiting connection verification.";
      this.lastTroubleshootingRecommendation =
        "Click 'Test AI Connection' in Profile & Settings or run an interview evaluation to verify live connectivity.";
    } else {
      this.cachedStatus = AiConnectionStatus.NOT_CONFIGURED;
      this.lastErrorCategory = "MISSING_API_KEY";
      this.lastMessage =
        "No AI_API_KEY is configured. Operating in deterministic rubric fallback mode.";
      this.lastTroubleshootingRecommendation =
        "Set AI_PROVIDER, AI_API_KEY, AI_BASE_URL, and AI_MODEL in your .env file or environment variables and restart the backend.";
    }
  }

  private static RestClient buildDefaultRestClient(int timeoutMs) {
    int safeTimeout = Math.max(1000, timeoutMs);
    SimpleClientHttpRequestFactory factory =
      new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(Math.min(5000, safeTimeout)));
    factory.setReadTimeout(Duration.ofMillis(safeTimeout));
    return RestClient.builder().requestFactory(factory).build();
  }

  private static String sanitizeApiKey(String rawKey) {
    if (rawKey == null) return "";
    String trimmed = rawKey.trim();
    if (
      (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
      (trimmed.startsWith("'") && trimmed.endsWith("'"))
    ) {
      trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
    }
    if (PLACEHOLDER_KEYS.contains(trimmed.toLowerCase())) {
      return "";
    }
    return trimmed;
  }

  public static String normalizeBaseUrl(String provider, String rawBaseUrl) {
    String candidate = rawBaseUrl != null ? rawBaseUrl.trim() : "";
    if (candidate.isEmpty()) {
      String p = provider != null ? provider.toLowerCase() : "";
      if (p.contains("gemini") || p.contains("google")) {
        return "https://generativelanguage.googleapis.com/v1beta/openai";
      }
      if (p.contains("groq")) {
        return "https://api.groq.com/openai/v1";
      }
      if (p.contains("ollama")) {
        return "http://localhost:11434/v1";
      }
      return "https://api.openai.com/v1";
    }
    while (candidate.endsWith("/")) {
      candidate = candidate.substring(0, candidate.length() - 1);
    }
    return candidate;
  }

  public static String resolveChatCompletionsUrl(String normalizedBaseUrl) {
    if (normalizedBaseUrl.endsWith("/chat/completions")) {
      return normalizedBaseUrl;
    }
    return normalizedBaseUrl + "/chat/completions";
  }

  private static String defaultModelForProvider(String provider) {
    String p = provider != null ? provider.toLowerCase() : "";
    if (p.contains("gemini") || p.contains("google")) {
      return "gemini-2.5-flash";
    }
    return "gpt-4o-mini";
  }

  public boolean isConfigured() {
    return !apiKey.isBlank() && !baseUrl.isBlank() && !model.isBlank();
  }

  public boolean shouldAttemptLiveCall() {
    return isConfigured();
  }

  public boolean isLiveConnected() {
    return isConfigured() && cachedStatus == AiConnectionStatus.CONNECTED;
  }

  public AiConnectionStatus getConnectionStatus() {
    if (!isConfigured()) {
      return AiConnectionStatus.NOT_CONFIGURED;
    }
    return cachedStatus != null
      ? cachedStatus
      : AiConnectionStatus.CONFIGURED_UNVERIFIED;
  }

  public String getActiveAiMode() {
    if (!isConfigured()) {
      return "FALLBACK_RUBRIC";
    }
    AiConnectionStatus status = getConnectionStatus();
    if (
      status == AiConnectionStatus.CONNECTED ||
      status == AiConnectionStatus.CONFIGURED_UNVERIFIED
    ) {
      return "LIVE_LLM";
    }
    return "FALLBACK_RUBRIC";
  }

  public String getProviderName() {
    return providerName;
  }

  public String getModel() {
    return model;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getChatCompletionsUrl() {
    return chatCompletionsUrl;
  }

  public String getLastErrorCategory() {
    return lastErrorCategory;
  }

  public String getLastTroubleshootingRecommendation() {
    return lastTroubleshootingRecommendation;
  }

  public Map<String, Object> getStatusMetadata() {
    AiConnectionStatus status = getConnectionStatus();
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("aiMode", getActiveAiMode());
    map.put("configured", isConfigured());
    map.put("liveLlmConfigured", isConfigured());
    map.put("connectionStatus", status.name());
    map.put(
      "provider",
      isConfigured() ? providerName : "DETERMINISTIC_FALLBACK_ENGINE"
    );
    map.put("configuredProvider", providerName);
    map.put("model", isConfigured() ? model : "rubric-v1");
    map.put("configuredModel", model);
    map.put("baseUrl", baseUrl);
    map.put(
      "lastTestedAt",
      lastTestedAt != null ? lastTestedAt.toString() : null
    );
    map.put("errorCategory", lastErrorCategory);
    map.put("message", lastMessage);
    map.put("troubleshootingRecommendation", lastTroubleshootingRecommendation);
    map.put("latencyMs", lastLatencyMs);
    return map;
  }

  public AiConnectionTestResultDto testConnection() {
    if (!isConfigured()) {
      this.cachedStatus = AiConnectionStatus.NOT_CONFIGURED;
      this.lastErrorCategory = "MISSING_API_KEY";
      this.lastTestedAt = Instant.now();
      this.lastLatencyMs = 0L;
      this.lastMessage =
        "AI provider credentials are not configured (AI_API_KEY is empty).";
      this.lastTroubleshootingRecommendation =
        "Set AI_PROVIDER, AI_API_KEY, AI_BASE_URL, and AI_MODEL in your .env file or environment variables, then restart the backend.";
      return toTestResultDto();
    }

    long startNanos = System.nanoTime();
    try {
      String reply = chatCompletion(
        "You are a connectivity check responder. Reply with the single word OK.",
        "Ping connectivity check.",
        0.0,
        128,
        false
      );
      long elapsedMs = Duration.ofNanos(
        System.nanoTime() - startNanos
      ).toMillis();
      recordSuccess(
        elapsedMs,
        "Connection verified with " +
          providerName +
          " (" +
          model +
          "). Model responded: " +
          (reply.length() > 40 ? reply.substring(0, 40) + "..." : reply)
      );
    } catch (AiCallException ex) {
      long elapsedMs = Duration.ofNanos(
        System.nanoTime() - startNanos
      ).toMillis();
      this.lastLatencyMs = elapsedMs;
    } catch (Exception ex) {
      long elapsedMs = Duration.ofNanos(
        System.nanoTime() - startNanos
      ).toMillis();
      AiCallException classified = classifyAndRecordFailure(ex);
      this.lastLatencyMs = elapsedMs;
      log.warn(
        "AI connection test failed [provider={}, model={}, category={}]: {}",
        providerName,
        model,
        classified.getErrorCategory(),
        classified.getMessage()
      );
    }
    return toTestResultDto();
  }

  private AiConnectionTestResultDto toTestResultDto() {
    return new AiConnectionTestResultDto(
      providerName,
      model,
      baseUrl,
      isConfigured(),
      isConfigured(),
      getActiveAiMode(),
      getConnectionStatus(),
      lastErrorCategory,
      lastMessage,
      lastTroubleshootingRecommendation,
      lastTestedAt,
      lastLatencyMs
    );
  }

  public String chatCompletion(
    String systemPrompt,
    String userPrompt,
    double temperature
  ) {
    return chatCompletion(systemPrompt, userPrompt, temperature, null, false);
  }

  public JsonNode chatCompletionJson(
    String systemPrompt,
    String userPrompt,
    double temperature
  ) {
    String rawContent;
    try {
      rawContent = chatCompletion(
        systemPrompt,
        userPrompt,
        temperature,
        null,
        true
      );
    } catch (AiCallException ex) {
      // Some OpenAI-compatible endpoints reject response_format: {"type": "json_object"} with HTTP 400
      if (
        ex.getHttpStatus() != null &&
        ex.getHttpStatus() == 400 &&
        ex.getMessage() != null &&
        ex.getMessage().toLowerCase().contains("response_format")
      ) {
        rawContent = chatCompletion(
          systemPrompt,
          userPrompt,
          temperature,
          null,
          false
        );
      } else {
        throw ex;
      }
    }
    return parseJsonContent(rawContent);
  }

  public String chatCompletion(
    String systemPrompt,
    String userPrompt,
    double temperature,
    Integer maxTokens,
    boolean jsonObjectFormat
  ) {
    if (!isConfigured()) {
      throw new AiCallException(
        "AI provider is not configured (missing AI_API_KEY).",
        AiConnectionStatus.NOT_CONFIGURED,
        "MISSING_API_KEY",
        null,
        "Set AI_API_KEY in .env or environment variables.",
        null
      );
    }

    long startNanos = System.nanoTime();
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", model);
      body.put("temperature", temperature);
      if (maxTokens != null && maxTokens > 0) {
        body.put("max_tokens", maxTokens);
      }
      if (jsonObjectFormat) {
        body.put("response_format", Map.of("type", "json_object"));
      }
      body.put(
        "messages",
        List.of(
          Map.of("role", "system", "content", systemPrompt),
          Map.of("role", "user", "content", userPrompt)
        )
      );

      String rawResponse = restClient
        .post()
        .uri(URI.create(chatCompletionsUrl))
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + apiKey)
        .body(body)
        .retrieve()
        .body(String.class);

      if (rawResponse == null || rawResponse.isBlank()) {
        throw new AiCallException(
          "Provider returned an empty HTTP response body.",
          AiConnectionStatus.PROVIDER_ERROR,
          "EMPTY_PROVIDER_RESPONSE",
          200,
          "Verify that AI_BASE_URL points to a valid OpenAI-compatible /chat/completions endpoint.",
          null
        );
      }

      JsonNode root;
      try {
        root = objectMapper.readTree(rawResponse);
      } catch (Exception parseEx) {
        throw new AiCallException(
          "Provider response is not valid JSON.",
          AiConnectionStatus.PROVIDER_ERROR,
          "MALFORMED_PROVIDER_RESPONSE",
          200,
          "Check AI_BASE_URL — the endpoint may have returned an HTML error page instead of Chat Completions JSON.",
          parseEx
        );
      }

      JsonNode choices = root.path("choices");
      if (!choices.isArray() || choices.isEmpty()) {
        throw new AiCallException(
          "Provider JSON response did not contain any choices.",
          AiConnectionStatus.PROVIDER_ERROR,
          "MISSING_CHOICES_IN_RESPONSE",
          200,
          "Verify model compatibility with OpenAI Chat Completions format.",
          null
        );
      }

      String content = choices
        .path(0)
        .path("message")
        .path("content")
        .asText("")
        .trim();
      if (content.isBlank()) {
        throw new AiCallException(
          "Provider returned a blank message content.",
          AiConnectionStatus.PROVIDER_ERROR,
          "EMPTY_MESSAGE_CONTENT",
          200,
          "Check model output token limits or safety filters.",
          null
        );
      }

      long elapsedMs = Duration.ofNanos(
        System.nanoTime() - startNanos
      ).toMillis();
      recordSuccess(
        elapsedMs,
        "Connected to " + providerName + " (" + model + ")."
      );
      return content;
    } catch (AiCallException ex) {
      recordFailureState(ex);
      throw ex;
    } catch (Exception ex) {
      throw classifyAndRecordFailure(ex);
    }
  }

  public JsonNode parseJsonContent(String content) {
    if (content == null || content.isBlank()) {
      throw new AiCallException(
        "Cannot parse empty LLM content as JSON.",
        AiConnectionStatus.PROVIDER_ERROR,
        "EMPTY_JSON_CONTENT",
        200,
        "Ensure the configured model supports structured JSON output.",
        null
      );
    }
    String cleaned = stripMarkdownCodeFences(content);
    try {
      return objectMapper.readTree(cleaned);
    } catch (Exception firstEx) {
      int firstBrace = cleaned.indexOf('{');
      int lastBrace = cleaned.lastIndexOf('}');
      if (firstBrace >= 0 && lastBrace > firstBrace) {
        String extracted = cleaned.substring(firstBrace, lastBrace + 1);
        try {
          return objectMapper.readTree(extracted);
        } catch (Exception ignored) {}
      }
      AiCallException ex = new AiCallException(
        "LLM returned malformed JSON content that could not be parsed.",
        AiConnectionStatus.PROVIDER_ERROR,
        "MALFORMED_JSON_OUTPUT",
        200,
        "Try a model with stronger structured JSON adherence (e.g., gemini-2.5-flash or gpt-4o-mini).",
        firstEx
      );
      recordFailureState(ex);
      throw ex;
    }
  }

  public static String stripMarkdownCodeFences(String raw) {
    String s = raw.trim();
    if (s.startsWith("```")) {
      int firstNewline = s.indexOf('\n');
      if (firstNewline > 0) {
        s = s.substring(firstNewline + 1);
      } else {
        s = s.substring(3);
      }
      if (s.endsWith("```")) {
        s = s.substring(0, s.length() - 3);
      }
    }
    return s.trim();
  }

  private void recordSuccess(long latencyMs, String msg) {
    this.cachedStatus = AiConnectionStatus.CONNECTED;
    this.lastErrorCategory = null;
    this.lastMessage = msg;
    this.lastTroubleshootingRecommendation =
      "Live LLM integration is healthy and active.";
    this.lastTestedAt = Instant.now();
    this.lastLatencyMs = latencyMs;
  }

  private void recordFailureState(AiCallException ex) {
    this.cachedStatus = ex.getConnectionStatus();
    this.lastErrorCategory = ex.getErrorCategory();
    this.lastMessage = ex.getMessage();
    this.lastTroubleshootingRecommendation =
      ex.getTroubleshootingRecommendation();
    this.lastTestedAt = Instant.now();
    log.warn(
      "AI provider call failed [provider={}, model={}, status={}, httpStatus={}, category={}]: {}",
      providerName,
      model,
      ex.getConnectionStatus(),
      ex.getHttpStatus(),
      ex.getErrorCategory(),
      ex.getMessage()
    );
  }

  private AiCallException classifyAndRecordFailure(Exception ex) {
    if (ex instanceof HttpStatusCodeException httpEx) {
      int code = httpEx.getStatusCode().value();
      String body = sanitizeErrorBody(httpEx.getResponseBodyAsString());
      String lowerBody = body.toLowerCase();

      if (
        code == 401 ||
        code == 403 ||
        lowerBody.contains("api_key_invalid") ||
        lowerBody.contains("invalid api key") ||
        lowerBody.contains("invalid_api_key") ||
        lowerBody.contains("unauthenticated") ||
        lowerBody.contains("unauthorized")
      ) {
        AiCallException callEx = new AiCallException(
          "Provider rejected authentication (HTTP " +
            code +
            "). The configured AI_API_KEY is invalid, expired, or incompatible with " +
            providerName +
            ".",
          AiConnectionStatus.AUTH_ERROR,
          "AUTHENTICATION_FAILED",
          code,
          "Verify your AI_API_KEY in .env. For Google Gemini (generativelanguage.googleapis.com), use a valid Gemini API key from Google AI Studio (starts with 'AIza...'), not an OAuth/Vertex token.",
          httpEx
        );
        recordFailureState(callEx);
        return callEx;
      }

      if (
        code == 402 ||
        (code == 429 &&
          (lowerBody.contains("insufficient_quota") ||
            lowerBody.contains("billing") ||
            lowerBody.contains("quota exceeded") ||
            lowerBody.contains("credit")))
      ) {
        AiCallException callEx = new AiCallException(
          "Provider reported a quota or billing restriction (HTTP " +
            code +
            ").",
          AiConnectionStatus.QUOTA_OR_BILLING_ERROR,
          "QUOTA_OR_BILLING_EXCEEDED",
          code,
          "Check your account billing status and usage quota in the " +
            providerName +
            " developer console.",
          httpEx
        );
        recordFailureState(callEx);
        return callEx;
      }

      if (code == 429) {
        AiCallException callEx = new AiCallException(
          "Provider rate limit reached (HTTP 429).",
          AiConnectionStatus.RATE_LIMITED,
          "RATE_LIMIT_EXCEEDED",
          code,
          "Wait a moment and retry, or switch to a higher-throughput model/tier.",
          httpEx
        );
        recordFailureState(callEx);
        return callEx;
      }

      if (
        code == 404 ||
        (code == 400 &&
          (lowerBody.contains("model") ||
            lowerBody.contains("not found") ||
            lowerBody.contains("does not exist") ||
            lowerBody.contains("unsupported")))
      ) {
        AiCallException callEx = new AiCallException(
          "Model '" +
            model +
            "' or endpoint '" +
            baseUrl +
            "' was not found or rejected (HTTP " +
            code +
            ").",
          AiConnectionStatus.MODEL_OR_ENDPOINT_ERROR,
          "INVALID_MODEL_OR_ENDPOINT",
          code,
          "Verify that AI_MODEL ('" +
            model +
            "') is supported by AI_BASE_URL ('" +
            baseUrl +
            "').",
          httpEx
        );
        recordFailureState(callEx);
        return callEx;
      }

      AiCallException callEx = new AiCallException(
        "Provider returned HTTP " + code + " error.",
        AiConnectionStatus.PROVIDER_ERROR,
        "HTTP_" + code + "_ERROR",
        code,
        "Inspect backend logs and verify AI_BASE_URL, AI_MODEL, and provider service status.",
        httpEx
      );
      recordFailureState(callEx);
      return callEx;
    }

    if (isTimeoutException(ex)) {
      AiCallException callEx = new AiCallException(
        "AI provider request timed out after " + timeoutMs + " ms.",
        AiConnectionStatus.TIMEOUT,
        "REQUEST_TIMEOUT",
        null,
        "Increase AI_TIMEOUT_MS in .env or check your network connection to " +
          baseUrl +
          ".",
        ex
      );
      recordFailureState(callEx);
      return callEx;
    }

    if (ex instanceof ResourceAccessException) {
      AiCallException callEx = new AiCallException(
        "Could not reach AI provider endpoint at " + baseUrl + ".",
        AiConnectionStatus.PROVIDER_ERROR,
        "NETWORK_UNREACHABLE",
        null,
        "Verify network access and check that AI_BASE_URL ('" +
          baseUrl +
          "') is reachable.",
        ex
      );
      recordFailureState(callEx);
      return callEx;
    }

    AiCallException callEx = new AiCallException(
      "Unexpected AI provider failure: " + ex.getClass().getSimpleName(),
      AiConnectionStatus.PROVIDER_ERROR,
      "UNEXPECTED_PROVIDER_ERROR",
      null,
      "Check server logs and verify provider configuration in .env.",
      ex
    );
    recordFailureState(callEx);
    return callEx;
  }

  private static boolean isTimeoutException(Throwable t) {
    Throwable curr = t;
    while (curr != null) {
      if (
        curr instanceof SocketTimeoutException ||
        curr instanceof HttpTimeoutException
      ) {
        return true;
      }
      String msg = curr.getMessage();
      if (
        msg != null &&
        (msg.toLowerCase().contains("timed out") ||
          msg.toLowerCase().contains("timeout"))
      ) {
        return true;
      }
      curr = curr.getCause();
    }
    return false;
  }

  private String sanitizeErrorBody(String rawBody) {
    if (rawBody == null || rawBody.isBlank()) {
      return "";
    }
    String sanitized = rawBody;
    if (!apiKey.isBlank()) {
      sanitized = sanitized.replace(apiKey, "[REDACTED]");
    }
    return sanitized.length() > 400 ? sanitized.substring(0, 400) : sanitized;
  }
}
