package com.interviewsimulator.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long validityMs;
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    public JwtTokenProvider(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expiration-ms:86400000}") long validityMs
    ) {
        String paddedSecret = secret.length() >= 64
                ? secret
                : (secret + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef").substring(0, 64);
        this.secretKey = Keys.hmacShaKeyFor(paddedSecret.getBytes(StandardCharsets.UTF_8));
        this.validityMs = validityMs;
    }

    public String generateToken(Long userId, String email, String displayName) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("displayName", displayName)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public UserPrincipal validateAndExtract(String token) {
        if (token == null || token.isBlank() || revokedTokens.contains(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.parseLong(claims.getSubject());
            String email = claims.get("email", String.class);
            String displayName = claims.get("displayName", String.class);
            return new UserPrincipal(userId, email, displayName);
        } catch (Exception e) {
            return null;
        }
    }

    public void revokeToken(String token) {
        if (token != null && !token.isBlank()) {
            revokedTokens.add(token);
        }
    }

    public long getValidityMs() {
        return validityMs;
    }
}
