package com.interviewsimulator.backend.auth;

import com.interviewsimulator.backend.common.ForbiddenOperationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ForbiddenOperationException("No authenticated user found in security context.");
        }
        return principal;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().id();
    }
}
