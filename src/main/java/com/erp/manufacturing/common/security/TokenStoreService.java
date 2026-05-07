package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.TokenRevokedException;
import com.erp.manufacturing.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed token store for refresh tokens, access token blacklist,
 * brute-force counters, and multi-device session tracking.
 *
 * <p>Key patterns:
 * <pre>
 *   auth:refresh:{userId}:{tokenId}       → refresh token value          TTL=7d
 *   auth:blacklist:{jti}                  → "1"                          TTL=remaining access token lifetime
 *   auth:failcount:{username}             → failure count                TTL=15m
 *   auth:session:device:{userId}:{deviceId} → last-seen IP + metadata   TTL=7d
 * </pre>
 *
 * <p><b>SCAN vs KEYS</b>: All multi-key deletions use cursor-based SCAN (O(1) per call)
 * instead of KEYS (O(N) blocking) to avoid Redis latency spikes in production.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenStoreService {

    private static final String REFRESH_KEY_PREFIX      = "auth:refresh:";
    private static final String BLACKLIST_KEY_PREFIX    = "auth:blacklist:";
    private static final String FAILCOUNT_KEY_PREFIX    = "auth:failcount:";
    private static final String SESSION_DEVICE_PREFIX   = "auth:session:device:";

    /** How many keys to fetch per SCAN iteration – keeps each call O(1). */
    private static final int SCAN_COUNT = 100;

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProperties jwtProperties;

    // ── Refresh Token ─────────────────────────────────────────────────────

    public void saveRefreshToken(UUID userId, String tokenId, String refreshToken) {
        String key = refreshKey(userId, tokenId);
        long ttlSeconds = jwtProperties.refreshTokenExpiryMs() / 1000;
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);
    }

    public String getRefreshToken(UUID userId, String tokenId) {
        return redisTemplate.opsForValue().get(refreshKey(userId, tokenId));
    }

    public void deleteRefreshToken(UUID userId, String tokenId) {
        redisTemplate.delete(refreshKey(userId, tokenId));
    }

    /**
     * Deletes ALL refresh tokens for a user (force logout all devices).
     *
     * <p>Uses cursor-based SCAN instead of KEYS to avoid blocking Redis.
     * Each SCAN call fetches {@value SCAN_COUNT} keys at a time,
     * yielding control back to Redis between iterations.
     */
    public void deleteAllUserTokens(UUID userId) {
        String pattern = REFRESH_KEY_PREFIX + userId + ":*";
        List<String> keysToDelete = scanKeys(pattern);
        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
            log.debug("[TokenStore] Deleted {} refresh tokens for userId={}", keysToDelete.size(), userId);
        }
    }

    // ── Access Token Blacklist ────────────────────────────────────────────

    public void blacklistAccessToken(String jti, long remainingTtlMs) {
        if (remainingTtlMs <= 0) return;
        redisTemplate.opsForValue().set(
                BLACKLIST_KEY_PREFIX + jti, "1",
                remainingTtlMs, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti));
    }

    /** Validates blacklist and throws if revoked. */
    public void assertNotBlacklisted(String jti) {
        if (isBlacklisted(jti)) throw new TokenRevokedException();
    }

    // ── Brute-force Protection ────────────────────────────────────────────

    public long incrementFailCount(String username) {
        String key = FAILCOUNT_KEY_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 15, TimeUnit.MINUTES);
        }
        return count != null ? count : 1;
    }

    public long getFailCount(String username) {
        String val = redisTemplate.opsForValue().get(FAILCOUNT_KEY_PREFIX + username);
        return val != null ? Long.parseLong(val) : 0;
    }

    public void resetFailCount(String username) {
        redisTemplate.delete(FAILCOUNT_KEY_PREFIX + username);
    }

    // ── Multi-Device Session ──────────────────────────────────────────────

    /**
     * Registers a device session, storing the device's last-seen IP.
     * Each device ({@code deviceId}) has an independent TTL equal to refresh token expiry.
     *
     * @param userId   owner of the session
     * @param deviceId stable client-generated device identifier
     * @param ip       current client IP
     */
    public void saveDeviceSession(UUID userId, String deviceId, String ip) {
        String key = deviceSessionKey(userId, deviceId);
        long ttlSeconds = jwtProperties.refreshTokenExpiryMs() / 1000;
        redisTemplate.opsForValue().set(key, ip, ttlSeconds, TimeUnit.SECONDS);
    }

    public String getDeviceSessionIp(UUID userId, String deviceId) {
        return redisTemplate.opsForValue().get(deviceSessionKey(userId, deviceId));
    }

    public void deleteDeviceSession(UUID userId, String deviceId) {
        redisTemplate.delete(deviceSessionKey(userId, deviceId));
    }

    /** Extends TTL of a device session on each successful token refresh. */
    public void extendDeviceSession(UUID userId, String deviceId) {
        long ttlSeconds = jwtProperties.refreshTokenExpiryMs() / 1000;
        redisTemplate.expire(deviceSessionKey(userId, deviceId), ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Clears ALL device sessions for a user (used by logout-all).
     * Safe: uses SCAN instead of KEYS.
     */
    public void deleteAllDeviceSessions(UUID userId) {
        String pattern = SESSION_DEVICE_PREFIX + userId + ":*";
        List<String> keysToDelete = scanKeys(pattern);
        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
            log.debug("[TokenStore] Deleted {} device sessions for userId={}", keysToDelete.size(), userId);
        }
    }

    /** Returns the number of active device sessions for a user. */
    public long countDeviceSessions(UUID userId) {
        String pattern = SESSION_DEVICE_PREFIX + userId + ":*";
        return scanKeys(pattern).size();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private String refreshKey(UUID userId, String tokenId) {
        return REFRESH_KEY_PREFIX + userId + ":" + tokenId;
    }

    private String deviceSessionKey(UUID userId, String deviceId) {
        return SESSION_DEVICE_PREFIX + userId + ":" + deviceId;
    }

    /**
     * Collects all Redis keys matching {@code pattern} using cursor-based SCAN.
     *
     * <p>Each iteration fetches at most {@value SCAN_COUNT} keys, making individual
     * calls O(1). This is safe to call on large keyspaces without blocking Redis.
     */
    private List<String> scanKeys(String pattern) {
        List<String> result = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                result.add(cursor.next());
            }
        } catch (Exception e) {
            log.error("[TokenStore] Error during SCAN for pattern={}: {}", pattern, e.getMessage());
        }
        return result;
    }
}
