package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
 *   auth:refresh:{userId}:{tokenId}:meta  → session start (epochMilli)   TTL=7d
 *   auth:refresh:owner:{tokenId}          → userId (reverse lookup)      TTL=7d
 *   auth:refresh:used:{tokenId}           → "1" (RTR reuse marker)       TTL=60s
 *   auth:refresh:lock:{tokenId}           → "1" (rotation-in-progress)   TTL=2s
 *   auth:refresh:rotated:{tokenId}        → new tokenId (rotation result) TTL=5s
 *   auth:blacklist:{jti}                  → "1"                          TTL=remaining access token lifetime
 *   auth:failcount:{username}             → failure count                TTL=15m
 *   auth:session:device:{userId}:{deviceId} → last-seen IP + metadata   TTL=7d
 * </pre>
 *
 * <p><b>Two keys, opposite requirements, same namespace</b> — do not "harmonise" them:
 * the {@code :meta} companion key matches {@code auth:refresh:{userId}:*} on purpose so
 * {@link #deleteAllUserTokens} sweeps it along with the token it belongs to, whereas
 * {@code auth:refresh:used:{tokenId}} deliberately falls <em>outside</em> that pattern so a
 * force-logout cannot erase the very marker that proves a reuse happened (B80).
 *
 * <p><b>SCAN vs KEYS</b>: All multi-key deletions use cursor-based SCAN (O(1) per call)
 * instead of KEYS (O(N) blocking) to avoid Redis latency spikes in production.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenStoreService {

    private static final String REFRESH_KEY_PREFIX      = "auth:refresh:";
    private static final String REFRESH_OWNER_KEY_PREFIX = "auth:refresh:owner:";
    private static final String REFRESH_USED_KEY_PREFIX = "auth:refresh:used:";
    private static final String REFRESH_LOCK_KEY_PREFIX  = "auth:refresh:lock:";
    private static final String REFRESH_ROTATED_KEY_PREFIX = "auth:refresh:rotated:";
    private static final String SESSION_START_KEY_SUFFIX = ":meta";
    private static final String BLACKLIST_KEY_PREFIX    = "auth:blacklist:";
    private static final String FAILCOUNT_KEY_PREFIX    = "auth:failcount:";
    private static final String SESSION_DEVICE_PREFIX   = "auth:session:device:";

    /** How long a rotated-away tokenId stays recognisable as "already used" (RTR window). */
    private static final long REUSE_DETECTION_TTL_SEC = 60L;

    /** How long the advisory per-tokenId rotation lock is held before it auto-expires. */
    private static final long REFRESH_LOCK_TTL_SEC = 2L;

    /**
     * How long a completed rotation's result stays discoverable by a racing duplicate. Deliberately
     * much shorter than {@link #REUSE_DETECTION_TTL_SEC} — it only needs to cover realistic
     * double-submit/retry timing, not to weaken the real reuse-detection boundary (a replay arriving
     * after this window still hits {@link #wasRefreshTokenUsed} normally).
     */
    private static final long ROTATION_RESULT_TTL_SEC = 5L;

    /** How many keys to fetch per SCAN iteration – keeps each call O(1). */
    private static final int SCAN_COUNT = 100;

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProperties jwtProperties;

    // ── Refresh Token ─────────────────────────────────────────────────────

    /**
     * Also writes the {@code auth:refresh:owner:{tokenId}} reverse-lookup key (P0 auth fix) — every
     * caller of this method (login, rotation) gets it automatically. This is what lets {@code
     * AuthService.refresh} identify the user from {@code tokenId} alone, without needing to parse an
     * access token out of the {@code Authorization} header at all.
     */
    public void saveRefreshToken(UUID userId, String tokenId, String refreshToken) {
        long ttlSeconds = jwtProperties.refreshTokenExpiryMs() / 1000;
        redisTemplate.opsForValue().set(refreshKey(userId, tokenId), refreshToken, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(REFRESH_OWNER_KEY_PREFIX + tokenId, userId.toString(), ttlSeconds, TimeUnit.SECONDS);
    }

    public String getRefreshToken(UUID userId, String tokenId) {
        return redisTemplate.opsForValue().get(refreshKey(userId, tokenId));
    }

    /**
     * Reverse lookup for {@code tokenId → userId} (P0 auth fix). Deliberately **not** explicitly
     * deleted on logout/rotation/force-logout — it self-expires via the same TTL as the refresh token
     * it describes, same as {@code :used}/{@code :rotated} already do. Leaving it briefly stale after
     * a delete is harmless: it only tells a caller which bucket to look in next, the actual
     * authorization decision is still {@code stored.equals(refreshToken)} against the primary key.
     *
     * @return the owning userId, or {@code null} if unknown (never existed, or expired).
     */
    public UUID getTokenOwner(String tokenId) {
        String value = redisTemplate.opsForValue().get(REFRESH_OWNER_KEY_PREFIX + tokenId);
        return value == null ? null : UUID.fromString(value);
    }

    /**
     * Removes a refresh token together with its session-start companion key.
     *
     * <p>Both keys are deleted: leaving the {@code :meta} key behind would keep an orphan alive
     * for the remaining refresh TTL after the token it describes is gone.
     */
    public void deleteRefreshToken(UUID userId, String tokenId) {
        redisTemplate.delete(List.of(refreshKey(userId, tokenId), sessionStartKey(userId, tokenId)));
    }

    // ── Absolute Session Timeout (D8b) ────────────────────────────────────

    /**
     * Records when the session behind {@code tokenId} started.
     *
     * <p>Written at login with "now", then carried forward unchanged on every rotation — the
     * absolute timeout measures the age of the <em>session</em>, not of the current token, so
     * re-stamping it here with the current time would silently disable the timeout (B81).
     */
    public void saveSessionStart(UUID userId, String tokenId, Instant startedAt) {
        long ttlSeconds = jwtProperties.refreshTokenExpiryMs() / 1000;
        redisTemplate.opsForValue().set(
                sessionStartKey(userId, tokenId),
                String.valueOf(startedAt.toEpochMilli()),
                ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * @return when the session behind {@code tokenId} started, or {@code null} when unknown —
     *         either the token is gone, or it belongs to a session established before {@code D8b}
     *         shipped. Callers decide what "unknown" means; see {@code AuthService.refresh}.
     */
    public Instant getSessionStart(UUID userId, String tokenId) {
        String value = redisTemplate.opsForValue().get(sessionStartKey(userId, tokenId));
        return value == null ? null : Instant.ofEpochMilli(Long.parseLong(value));
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

    // ── Refresh Token Reuse Detection (RTR) ───────────────────────────────

    /**
     * Marks a tokenId as "already rotated away" for {@value #REUSE_DETECTION_TTL_SEC} seconds.
     *
     * <p>Keyed by {@code tokenId} alone (no {@code userId} segment) — the tokenId is globally
     * unique, and the reuse check runs before the caller's identity has been re-established
     * against a stored token.
     *
     * <p>The key deliberately outlives the refresh token itself: that short window is exactly
     * what lets {@link #wasRefreshTokenUsed} tell a stolen-and-replayed token apart from one
     * that simply expired.
     */
    public void markRefreshTokenUsed(String tokenId) {
        redisTemplate.opsForValue().set(
                REFRESH_USED_KEY_PREFIX + tokenId, "1",
                REUSE_DETECTION_TTL_SEC, TimeUnit.SECONDS);
    }

    /** @return true if this tokenId was rotated away within the reuse-detection window. */
    public boolean wasRefreshTokenUsed(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_USED_KEY_PREFIX + tokenId));
    }

    // ── Concurrent Refresh Race (advisory lock + rotation-result breadcrumb) ──

    /**
     * Best-effort advisory lock on {@code tokenId}, held only for the brief window a rotation takes.
     * Backed by a single atomic {@code SET NX PX} — {@link #REFRESH_LOCK_TTL_SEC} bounds the wait a
     * genuinely-racing duplicate request pays; no explicit unlock (the critical section is Redis-only
     * and completes in low single-digit milliseconds, so early release isn't worth a compare-and-delete).
     *
     * @return true if this caller acquired the lock (no one else is mid-rotation for this tokenId
     *         right now); false if another request already holds it.
     */
    public boolean acquireRefreshLock(String tokenId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                REFRESH_LOCK_KEY_PREFIX + tokenId, "1", REFRESH_LOCK_TTL_SEC, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Records that {@code oldTokenId} was just rotated into {@code newTokenId}, so a duplicate
     * request racing on the same old tokenId can be handed the one resulting pair instead of being
     * misdiagnosed as a reuse attack (see {@code AuthService.refresh}, {@code stored == null} branch).
     */
    public void saveRotationResult(String oldTokenId, String newTokenId) {
        redisTemplate.opsForValue().set(
                REFRESH_ROTATED_KEY_PREFIX + oldTokenId, newTokenId,
                ROTATION_RESULT_TTL_SEC, TimeUnit.SECONDS);
    }

    /** @return the tokenId {@code oldTokenId} was rotated into, if that happened within the last
     *          {@value #ROTATION_RESULT_TTL_SEC} seconds; {@code null} otherwise. */
    public String getRotationResult(String oldTokenId) {
        return redisTemplate.opsForValue().get(REFRESH_ROTATED_KEY_PREFIX + oldTokenId);
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
        if (isBlacklisted(jti)) throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_REVOKED);
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

    /**
     * Companion key of {@link #refreshKey}. The shared {@code auth:refresh:{userId}:} prefix is
     * load-bearing: it puts this key inside the SCAN pattern of {@link #deleteAllUserTokens}.
     */
    private String sessionStartKey(UUID userId, String tokenId) {
        return refreshKey(userId, tokenId) + SESSION_START_KEY_SUFFIX;
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
