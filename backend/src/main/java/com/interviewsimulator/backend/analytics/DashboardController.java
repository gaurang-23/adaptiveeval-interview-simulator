package com.interviewsimulator.backend.analytics;

import com.interviewsimulator.backend.ai.AiInterviewerService;
import com.interviewsimulator.backend.ai.dto.AiConnectionTestResultDto;
import com.interviewsimulator.backend.analytics.dto.DashboardDto;
import com.interviewsimulator.backend.auth.SecurityUtils;
import com.interviewsimulator.backend.execution.CodeExecutionClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final AnalyticsService analyticsService;
    private final AiInterviewerService aiInterviewerService;
    private final CodeExecutionClient executionClient;

    public DashboardController(
            AnalyticsService analyticsService,
            AiInterviewerService aiInterviewerService,
            CodeExecutionClient executionClient
    ) {
        this.analyticsService = analyticsService;
        this.aiInterviewerService = aiInterviewerService;
        this.executionClient = executionClient;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto> getDashboard() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(analyticsService.getDashboardData(userId));
    }

    @GetMapping("/system/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        return ResponseEntity.ok(Map.of(
                "ai", aiInterviewerService.getAiStatusMetadata(),
                "runner", executionClient.checkRunnerHealth()
        ));
    }

    @PostMapping("/system/ai/test")
    public ResponseEntity<AiConnectionTestResultDto> testAiConnection() {
        SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(aiInterviewerService.testConnection());
    }
}
