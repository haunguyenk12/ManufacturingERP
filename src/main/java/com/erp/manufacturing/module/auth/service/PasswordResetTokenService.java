package com.erp.manufacturing.module.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed single-use password reset tokens (D8c).
 *
 * <p>Key patterns:
 * <pre>
 *   auth:reset:{token}       → userId (string)   TTL 15m
 *   auth:reset:user:{userId} → token  (string)   TTL 15m
 * </pre>
 *
 * <p>The reverse index ({@code auth:reset:user:{userId}}) is what lets {@link #generateToken} enforce
 * "no accumulation": a new forgot-password request invalidates whatever token that user was issued
 * last, instead of leaving multiple live tokens usable at once.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private static final String TOKEN_KEY_PREFIX = "auth:reset:";
    private static final String USER_KEY_PREFIX  = "auth:reset:user:";
    private static final long   TOKEN_TTL_MINUTES = 15L;

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Issues a new reset token for {@code userId}, invalidating any token previously issued to them.
     */
    public String generateToken(UUID userId) {
        String userKey = userKey(userId);
        String oldToken = redisTemplate.opsForValue().get(userKey);
        if (oldToken != null) {
            redisTemplate.delete(tokenKey(oldToken));
        }

        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(tokenKey(token), userId.toString(), TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(userKey, token, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        return token;
    }

    /** @return the userId the token was issued for, or empty if the token is unknown or expired. */
    public Optional<UUID> resolveUserId(String token) {
        String value = redisTemplate.opsForValue().get(tokenKey(token));
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    /** Deletes the token (single-use) together with its reverse-index entry. */
    public void invalidate(String token, UUID userId) {
        redisTemplate.delete(tokenKey(token));
        redisTemplate.delete(userKey(userId));
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    private String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }
}
