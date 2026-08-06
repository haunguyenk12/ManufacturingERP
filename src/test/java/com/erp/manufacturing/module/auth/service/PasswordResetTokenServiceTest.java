package com.erp.manufacturing.module.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordResetTokenService} (D8c). {@link RedisTemplate} is mocked, same
 * approach {@code TokenStoreServiceTest} uses — no Testcontainers/embedded Redis needed for a plain
 * key/TTL contract.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetTokenService Unit Tests")
class PasswordResetTokenServiceTest {

    private static final long TOKEN_TTL_MINUTES = 15L;

    @Mock private RedisTemplate<String, String>   redis;
    @Mock private ValueOperations<String, String> valueOps;

    private PasswordResetTokenService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PasswordResetTokenService(redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("generateToken – writes both the forward and reverse-index keys with a 15m TTL")
    void generateToken_writesBothKeysWithFifteenMinuteTtl() {
        when(valueOps.get("auth:reset:user:" + userId)).thenReturn(null);

        String token = service.generateToken(userId);

        verify(valueOps).set(eq("auth:reset:" + token), eq(userId.toString()),
                eq(TOKEN_TTL_MINUTES), eq(TimeUnit.MINUTES));
        verify(valueOps).set(eq("auth:reset:user:" + userId), eq(token),
                eq(TOKEN_TTL_MINUTES), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("generateToken – a second call for the same user deletes the previous token (no accumulation)")
    void generateToken_secondCallInvalidatesThePreviousToken() {
        when(valueOps.get("auth:reset:user:" + userId)).thenReturn("old-token");

        service.generateToken(userId);

        verify(redis).delete("auth:reset:old-token");
    }

    @Test
    @DisplayName("generateToken – first call for a user does not attempt to delete anything")
    void generateToken_firstCallDeletesNothing() {
        when(valueOps.get("auth:reset:user:" + userId)).thenReturn(null);

        service.generateToken(userId);

        verify(redis, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("resolveUserId – returns the userId for a known token")
    void resolveUserId_knownToken_returnsUserId() {
        when(valueOps.get("auth:reset:tok-1")).thenReturn(userId.toString());

        assertThat(service.resolveUserId("tok-1")).contains(userId);
    }

    @Test
    @DisplayName("resolveUserId – returns empty for an unknown or expired token")
    void resolveUserId_unknownToken_returnsEmpty() {
        when(valueOps.get("auth:reset:tok-1")).thenReturn(null);

        assertThat(service.resolveUserId("tok-1")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("invalidate – deletes both the token key and the reverse-index key")
    void invalidate_deletesBothKeys() {
        service.invalidate("tok-1", userId);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.times(2)).delete(keys.capture());
        assertThat(keys.getAllValues()).containsExactly(
                "auth:reset:tok-1", "auth:reset:user:" + userId);
    }
}
