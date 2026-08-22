package com.erp.manufacturing.module.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local userId = redis.call('GET', KEYS[1])
            if not userId then return nil end
            redis.call('DEL', KEYS[1])
            local userKey = ARGV[1] .. userId
            if redis.call('GET', userKey) == ARGV[2] then redis.call('DEL', userKey) end
            return userId
            """, String.class);

    private static final String TOKEN_KEY_PREFIX = "auth:reset:";
    private static final String USER_KEY_PREFIX  = "auth:reset:user:";
    private static final long   TOKEN_TTL_MINUTES = 15L;

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Issues a new reset token for {@code userId}, invalidating any token previously issued to them.
     */
    public String generateToken(UUID userId) {
        String userKey = userKey(userId);
        String oldTokenHash = redisTemplate.opsForValue().get(userKey);
        if (oldTokenHash != null) {
            redisTemplate.delete(tokenKey(oldTokenHash));
        }

        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = hash(token);
        redisTemplate.opsForValue().set(tokenKey(tokenHash), userId.toString(), TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(userKey, tokenHash, TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
        return token;
    }

    /** @return the userId the token was issued for, or empty if the token is unknown or expired. */
    public Optional<UUID> resolveUserId(String token) {
        String value = redisTemplate.opsForValue().get(tokenKey(hash(token)));
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    /** Atomically consumes a token before the password mutation starts. */
    public Optional<UUID> consumeUserId(String token) {
        String tokenHash = hash(token);
        String value = redisTemplate.execute(CONSUME_SCRIPT, java.util.List.of(tokenKey(tokenHash)),
                USER_KEY_PREFIX, tokenHash);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    /** Deletes the token (single-use) together with its reverse-index entry. */
    public void invalidate(String token, UUID userId) {
        redisTemplate.delete(tokenKey(hash(token)));
        redisTemplate.delete(userKey(userId));
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    private String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}
