package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenStoreService}.
 *
 * <p>{@link RedisTemplate} is mocked – no Testcontainers, no embedded Redis (D2).
 * What must not silently drift is locked here: the exact key strings, the TTL values,
 * and the conditional branches (SCAN with/without hits, non-positive blacklist TTL,
 * first vs. subsequent failure counter).
 *
 * <p>{@link JwtProperties} is a plain value record, so a real instance is used rather
 * than a mock (rule R3).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenStoreService Unit Tests")
class TokenStoreServiceTest {

    private static final long REFRESH_EXPIRY_MS  = 604_800_000L;   // 7 days
    private static final long REFRESH_EXPIRY_SEC = 604_800L;
    private static final long ABSOLUTE_TIMEOUT_MS = 2_592_000_000L; // 30 days

    @Mock private RedisTemplate<String, String>   redis;
    @Mock private ValueOperations<String, String> valueOps;

    private TokenStoreService store;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new TokenStoreService(
                redis,
                new JwtProperties("test-secret-key-that-is-at-least-256-bits-long!!", 900_000L,
                        REFRESH_EXPIRY_MS, ABSOLUTE_TIMEOUT_MS));
    }

    /** A closeable SCAN cursor mock that yields {@code keys} once, then reports exhaustion. */
    @SuppressWarnings("unchecked")
    private Cursor<String> cursorOf(String... keys) {
        Cursor<String> cursor = mock(Cursor.class);
        Iterator<String> iterator = List.of(keys).iterator();
        when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        lenient().when(cursor.next()).thenAnswer(invocation -> iterator.next());
        return cursor;
    }

    // ── Refresh token ──────────────────────────────────────────────────────

    @Test
    @DisplayName("saveRefreshToken – writes auth:refresh:{userId}:{tokenId} with the refresh TTL")
    void saveRefreshToken_setsKeyWithRefreshTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        store.saveRefreshToken(userId, "tid-1", "refresh-token");

        verify(valueOps).set(
                eq("auth:refresh:" + userId + ":tid-1"),
                eq("refresh-token"),
                eq(REFRESH_EXPIRY_SEC),
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("getRefreshToken / deleteRefreshToken – use the same key pattern as save")
    void getAndDeleteRefreshToken_useSameKeyPattern() {
        when(redis.opsForValue()).thenReturn(valueOps);
        String key = "auth:refresh:" + userId + ":tid-1";
        when(valueOps.get(key)).thenReturn("refresh-token");

        assertThat(store.getRefreshToken(userId, "tid-1")).isEqualTo("refresh-token");

        store.deleteRefreshToken(userId, "tid-1");
        verify(redis).delete(List.of(key, key + ":meta"));
    }

    @Test
    @DisplayName("deleteRefreshToken – removes the session-start companion key too, not just the token")
    void deleteRefreshToken_alsoRemovesTheSessionStartKey() {
        store.deleteRefreshToken(userId, "tid-1");

        // Deleting only the token would leave a :meta orphan alive for the rest of the refresh TTL.
        verify(redis).delete(List.of(
                "auth:refresh:" + userId + ":tid-1",
                "auth:refresh:" + userId + ":tid-1:meta"));
    }

    @Test
    @DisplayName("deleteAllUserTokens – SCANs auth:refresh:{userId}:* and deletes every hit")
    void deleteAllUserTokens_scanReturnsKeys_deletesThem() {
        String k1 = "auth:refresh:" + userId + ":t1";
        String k2 = "auth:refresh:" + userId + ":t2";
        Cursor<String> cursor = cursorOf(k1, k2);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        store.deleteAllUserTokens(userId);

        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redis).scan(options.capture());
        assertThat(options.getValue().getPattern()).isEqualTo("auth:refresh:" + userId + ":*");
        verify(redis).delete(List.of(k1, k2));
    }

    @Test
    @DisplayName("deleteAllUserTokens – empty SCAN result does not issue a delete")
    void deleteAllUserTokens_scanEmpty_doesNotCallDelete() {
        Cursor<String> emptyCursor = cursorOf();
        when(redis.scan(any(ScanOptions.class))).thenReturn(emptyCursor);

        store.deleteAllUserTokens(userId);

        verify(redis, never()).delete(anyCollection());
        verify(redis, never()).delete(anyString());
    }

    // ── Refresh token reuse detection (RTR) ────────────────────────────────

    @Test
    @DisplayName("markRefreshTokenUsed – writes auth:refresh:used:{tokenId} with a 60-second TTL")
    void markRefreshTokenUsed_setsKeyWithSixtySecondTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        store.markRefreshTokenUsed("tid-1");

        // Literal 60, not the private constant: the window length is the contract RTR depends on.
        verify(valueOps).set(eq("auth:refresh:used:tid-1"), eq("1"), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("wasRefreshTokenUsed – existing key means the tokenId was rotated away")
    void wasRefreshTokenUsed_keyExists_returnsTrue() {
        when(redis.hasKey("auth:refresh:used:tid-1")).thenReturn(true);

        assertThat(store.wasRefreshTokenUsed("tid-1")).isTrue();
    }

    @Test
    @DisplayName("wasRefreshTokenUsed – missing key (null hasKey) is not a reuse")
    void wasRefreshTokenUsed_keyMissing_returnsFalse() {
        when(redis.hasKey("auth:refresh:used:tid-1")).thenReturn(null);

        assertThat(store.wasRefreshTokenUsed("tid-1")).isFalse();
    }

    @Test
    @DisplayName("markRefreshTokenUsed – the used-marker key is outside the deleteAllUserTokens SCAN pattern")
    void markRefreshTokenUsed_keyIsNotSweptByDeleteAllUserTokens() {
        when(redis.opsForValue()).thenReturn(valueOps);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        store.markRefreshTokenUsed("tid-1");

        verify(valueOps).set(key.capture(), anyString(), anyLong(), any(TimeUnit.class));
        // deleteAllUserTokens sweeps "auth:refresh:{userId}:*" — force-logout must not wipe the
        // very marker that proves a reuse happened.
        assertThat(key.getValue()).doesNotStartWith("auth:refresh:" + userId + ":");
    }

    // ── Absolute session timeout (D8b) ─────────────────────────────────────

    @Test
    @DisplayName("saveSessionStart – writes auth:refresh:{userId}:{tokenId}:meta with the refresh TTL")
    void saveSessionStart_setsKeyWithRefreshTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        store.saveSessionStart(userId, "tid-1", Instant.ofEpochMilli(1_700_000_000_000L));

        verify(valueOps).set(
                eq("auth:refresh:" + userId + ":tid-1:meta"),
                eq("1700000000000"),
                eq(REFRESH_EXPIRY_SEC),
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("getSessionStart – round-trips the stored instant, and returns null when unknown")
    void getSessionStart_readsBackTheStoredInstant_nullWhenAbsent() {
        when(redis.opsForValue()).thenReturn(valueOps);
        String key = "auth:refresh:" + userId + ":tid-1:meta";
        when(valueOps.get(key)).thenReturn("1700000000000");

        assertThat(store.getSessionStart(userId, "tid-1"))
                .isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));

        // Absent key = session predating D8b, or a token that is simply gone. Not an error.
        when(valueOps.get(key)).thenReturn(null);
        assertThat(store.getSessionStart(userId, "tid-1")).isNull();
    }

    @Test
    @DisplayName("saveSessionStart – the :meta key IS inside the deleteAllUserTokens SCAN pattern")
    void sessionStartKey_isSweptByDeleteAllUserTokens() {
        when(redis.opsForValue()).thenReturn(valueOps);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        store.saveSessionStart(userId, "tid-1", Instant.ofEpochMilli(1_700_000_000_000L));

        verify(valueOps).set(key.capture(), anyString(), anyLong(), any(TimeUnit.class));
        // Deliberately the OPPOSITE requirement of the RTR marker directly above: this key must be
        // swept by force-logout, so it has to share the "auth:refresh:{userId}:" prefix. Two keys,
        // two opposite requirements, one namespace — the pair of tests is the record of that.
        assertThat(key.getValue()).startsWith("auth:refresh:" + userId + ":");
    }

    // ── Access token blacklist ─────────────────────────────────────────────

    @Test
    @DisplayName("blacklistAccessToken – positive TTL writes auth:blacklist:{jti} in milliseconds")
    void blacklistAccessToken_positiveTtl_setsKey() {
        when(redis.opsForValue()).thenReturn(valueOps);

        store.blacklistAccessToken("jti-1", 120_000L);

        verify(valueOps).set(eq("auth:blacklist:jti-1"), eq("1"), eq(120_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("blacklistAccessToken – non-positive TTL is a no-op (already-expired token)")
    void blacklistAccessToken_nonPositiveTtl_doesNothing() {
        store.blacklistAccessToken("jti-1", 0L);

        verifyNoInteractions(valueOps);
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("isBlacklisted – null hasKey result is treated as not blacklisted")
    void isBlacklisted_nullHasKey_returnsFalse() {
        when(redis.hasKey("auth:blacklist:jti-1")).thenReturn(null);

        assertThat(store.isBlacklisted("jti-1")).isFalse();
    }

    @Test
    @DisplayName("assertNotBlacklisted – revoked token throws AppException TOKEN_REVOKED")
    void assertNotBlacklisted_whenBlacklisted_throwsTokenRevoked() {
        when(redis.hasKey("auth:blacklist:jti-1")).thenReturn(true);

        assertThatThrownBy(() -> store.assertNotBlacklisted("jti-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.TOKEN_REVOKED));
    }

    // ── Brute-force counter ────────────────────────────────────────────────

    @Test
    @DisplayName("incrementFailCount – first failure also sets the 15-minute window")
    void incrementFailCount_firstFailure_setsExpiry() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("auth:failcount:testuser")).thenReturn(1L);

        assertThat(store.incrementFailCount("testuser")).isEqualTo(1L);

        verify(redis).expire("auth:failcount:testuser", 15, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("incrementFailCount – subsequent failure must not reset the window")
    void incrementFailCount_subsequentFailure_doesNotResetExpiry() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("auth:failcount:testuser")).thenReturn(3L);

        assertThat(store.incrementFailCount("testuser")).isEqualTo(3L);

        verify(redis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("getFailCount – missing key counts as zero failures")
    void getFailCount_noKey_returnsZero() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:failcount:testuser")).thenReturn(null);

        assertThat(store.getFailCount("testuser")).isZero();
    }

    @Test
    @DisplayName("getFailCount – stored value is parsed")
    void getFailCount_existingKey_returnsParsedValue() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:failcount:testuser")).thenReturn("4");

        assertThat(store.getFailCount("testuser")).isEqualTo(4L);
    }

    @Test
    @DisplayName("resetFailCount – deletes auth:failcount:{username}")
    void resetFailCount_deletesKey() {
        store.resetFailCount("testuser");

        verify(redis).delete("auth:failcount:testuser");
    }

    // ── Device sessions ────────────────────────────────────────────────────

    @Test
    @DisplayName("saveDeviceSession – writes auth:session:device:{userId}:{deviceId} with the refresh TTL")
    void saveDeviceSession_setsKeyWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        store.saveDeviceSession(userId, "device-1", "10.0.0.1");

        verify(valueOps).set(
                eq("auth:session:device:" + userId + ":device-1"),
                eq("10.0.0.1"),
                eq(REFRESH_EXPIRY_SEC),
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("extendDeviceSession – slides the TTL of the existing device key")
    void extendDeviceSession_expiresExistingKey() {
        store.extendDeviceSession(userId, "device-1");

        verify(redis).expire(
                "auth:session:device:" + userId + ":device-1",
                REFRESH_EXPIRY_SEC,
                TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("deleteAllDeviceSessions – SCANs auth:session:device:{userId}:* and deletes every hit")
    void deleteAllDeviceSessions_scanReturnsKeys_deletesThem() {
        String k1 = "auth:session:device:" + userId + ":d1";
        Cursor<String> cursor = cursorOf(k1);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        store.deleteAllDeviceSessions(userId);

        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redis).scan(options.capture());
        assertThat(options.getValue().getPattern()).isEqualTo("auth:session:device:" + userId + ":*");
        verify(redis).delete(List.of(k1));
    }

    @Test
    @DisplayName("countDeviceSessions – counts the keys returned by SCAN")
    void countDeviceSessions_scanCount() {
        Cursor<String> cursor = cursorOf(
                "auth:session:device:" + userId + ":d1",
                "auth:session:device:" + userId + ":d2",
                "auth:session:device:" + userId + ":d3");
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        assertThat(store.countDeviceSessions(userId)).isEqualTo(3L);
    }
}
