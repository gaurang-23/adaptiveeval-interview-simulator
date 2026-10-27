package com.interviewsimulator.backend.auth;

import com.interviewsimulator.backend.auth.dto.AuthResponse;
import com.interviewsimulator.backend.auth.dto.ChangePasswordRequest;
import com.interviewsimulator.backend.auth.dto.LoginRequest;
import com.interviewsimulator.backend.auth.dto.RegisterRequest;
import com.interviewsimulator.backend.common.BadRequestException;
import com.interviewsimulator.backend.common.CompetencyType;
import com.interviewsimulator.backend.common.ResourceNotFoundException;
import com.interviewsimulator.backend.skills.UserSkillProfileEntity;
import com.interviewsimulator.backend.skills.UserSkillProfileRepository;
import com.interviewsimulator.backend.user.UserEntity;
import com.interviewsimulator.backend.user.UserRepository;
import com.interviewsimulator.backend.user.dto.UserProfileDto;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSkillProfileRepository skillProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            UserSkillProfileRepository skillProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.skillProfileRepository = skillProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("An account with this email address already exists.");
        }

        UserEntity user = new UserEntity();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setTargetRole(request.targetRole() != null && !request.targetRole().isBlank()
                ? request.targetRole().trim()
                : "Software Engineer");
        user.setExperienceLevel(request.experienceLevel() != null && !request.experienceLevel().isBlank()
                ? request.experienceLevel().trim()
                : "Mid-Level");
        user.setPreferredLanguage("JAVA");

        UserEntity savedUser = userRepository.save(user);

        for (CompetencyType competency : CompetencyType.values()) {
            UserSkillProfileEntity profile = new UserSkillProfileEntity();
            profile.setUserId(savedUser.getId());
            profile.setCompetency(competency.name());
            profile.setScore(50.0);
            profile.setConfidence(0.0);
            profile.setAssessmentsCount(0);
            skillProfileRepository.save(profile);
        }

        String token = jwtTokenProvider.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getDisplayName());
        return new AuthResponse(token, "Bearer", jwtTokenProvider.getValidityMs(), toDto(savedUser));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getDisplayName());
        return new AuthResponse(token, "Bearer", jwtTokenProvider.getValidityMs(), toDto(user));
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwtTokenProvider.revokeToken(authorizationHeader.substring(7).trim());
        }
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    public static UserProfileDto toDto(UserEntity user) {
        return new UserProfileDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTargetRole(),
                user.getExperienceLevel(),
                user.getPreferredLanguage(),
                user.getThemePreference(),
                user.getCreatedAt()
        );
    }
}
