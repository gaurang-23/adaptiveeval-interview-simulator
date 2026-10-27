package com.interviewsimulator.backend.user;

import com.interviewsimulator.backend.analytics.AnalyticsService;
import com.interviewsimulator.backend.analytics.dto.DashboardDto;
import com.interviewsimulator.backend.analytics.dto.DashboardDto.SkillMetricDto;
import com.interviewsimulator.backend.auth.AuthService;
import com.interviewsimulator.backend.auth.SecurityUtils;
import com.interviewsimulator.backend.auth.dto.ChangePasswordRequest;
import com.interviewsimulator.backend.recommendation.RecommendationDto;
import com.interviewsimulator.backend.recommendation.RecommendationService;
import com.interviewsimulator.backend.user.dto.UpdateProfileRequest;
import com.interviewsimulator.backend.user.dto.UserProfileDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final AnalyticsService analyticsService;
    private final RecommendationService recommendationService;

    public UserController(
            UserService userService,
            AuthService authService,
            AnalyticsService analyticsService,
            RecommendationService recommendationService
    ) {
        this.userService = userService;
        this.authService = authService;
        this.analyticsService = analyticsService;
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<UserProfileDto> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping
    public ResponseEntity<UserProfileDto> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PatchMapping("/theme")
    public ResponseEntity<UserProfileDto> updateTheme(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.updateThemePreference(userId, body.get("theme")));
    }

    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
    }

    @GetMapping("/skills")
    public ResponseEntity<List<SkillMetricDto>> getSkills() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(analyticsService.getUserSkills(userId));
    }

    @GetMapping("/progress")
    public ResponseEntity<DashboardDto> getProgress() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(analyticsService.getDashboardData(userId));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationDto>> getRecommendations() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recommendationService.getOrGenerateRecommendations(userId));
    }

    @PostMapping("/recommendations/refresh")
    public ResponseEntity<List<RecommendationDto>> refreshRecommendations() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recommendationService.refreshRecommendationsForUser(userId));
    }

    @PatchMapping("/recommendations/{id}")
    public ResponseEntity<RecommendationDto> updateRecommendationStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        String status = body.getOrDefault("status", "COMPLETED");
        return ResponseEntity.ok(recommendationService.updateRecommendationStatus(userId, id, status));
    }
}
