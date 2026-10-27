package com.interviewsimulator.backend.user;

import com.interviewsimulator.backend.auth.AuthService;
import com.interviewsimulator.backend.common.BadRequestException;
import com.interviewsimulator.backend.common.ResourceNotFoundException;
import com.interviewsimulator.backend.user.dto.UpdateProfileRequest;
import com.interviewsimulator.backend.user.dto.UserProfileDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        return AuthService.toDto(user);
    }

    @Transactional
    public UserProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setDisplayName(request.displayName().trim());
        user.setTargetRole(request.targetRole().trim());
        user.setExperienceLevel(request.experienceLevel().trim());
        if (request.themePreference() != null && !request.themePreference().isBlank()) {
            user.setThemePreference(normalizeTheme(request.themePreference()));
        }
        return AuthService.toDto(userRepository.save(user));
    }

    @Transactional
    public UserProfileDto updateThemePreference(Long userId, String theme) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.setThemePreference(normalizeTheme(theme));
        return AuthService.toDto(userRepository.save(user));
    }

    private String normalizeTheme(String raw) {
        if (raw == null) {
            throw new BadRequestException("Theme preference is required.");
        }
        String norm = raw.trim().toLowerCase();
        if (!"dark".equals(norm) && !"light".equals(norm)) {
            throw new BadRequestException("Theme preference must be 'dark' or 'light'.");
        }
        return norm;
    }
}
