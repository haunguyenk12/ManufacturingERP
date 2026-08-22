package com.erp.manufacturing.common.security;

import com.erp.manufacturing.config.JwtProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class TokenStoreConcurrencyIT {

    private static final String REDIS_PASSWORD = "integration-test-redis-password";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "no", "--requirepass", REDIS_PASSWORD);

    private static LettuceConnectionFactory connectionFactory;
    private static TokenStoreService store;

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        configuration.setPassword(RedisPassword.of(REDIS_PASSWORD));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.afterPropertiesSet();
        store = new TokenStoreService(template, new JwtProperties(
                "integration-test-jwt-secret-at-least-32-bytes",
                900_000L, 604_800_000L, 2_592_000_000L));
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @BeforeEach
    void clearRedis() {
        connectionFactory.getConnection().serverCommands().flushDb();
    }

    @Test
    void exactlyOneConcurrentRotationWinsAndNoRawSecretIsStored() throws Exception {
        UUID userId = UUID.randomUUID();
        String oldTokenId = UUID.randomUUID().toString();
        String oldSecret = "old-refresh-secret-with-enough-randomness";
        Instant sessionStart = Instant.now().minusSeconds(60);
        store.saveRefreshToken(userId, oldTokenId, oldSecret, 3L);
        store.saveSessionStart(userId, oldTokenId, sessionStart);

        int contenders = 16;
        var pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<TokenStoreService.RotationResult>> futures = new ArrayList<>();
        for (int index = 0; index < contenders; index++) {
            String newTokenId = UUID.randomUUID().toString();
            String newSecret = "new-refresh-secret-" + index;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return store.rotateRefreshToken(userId, oldTokenId, oldSecret,
                        newTokenId, newSecret, sessionStart, 3L);
            }));
        }
        ready.await();
        start.countDown();

        List<TokenStoreService.RotationResult> results = new ArrayList<>();
        for (var future : futures) results.add(future.get());
        pool.shutdownNow();

        assertThat(results).filteredOn(result -> result == TokenStoreService.RotationResult.ROTATED)
                .hasSize(1);
        assertThat(store.getRefreshToken(userId, oldTokenId)).isNull();
        assertThat(store.wasRefreshTokenUsed(oldTokenId, oldSecret, 3L)).isTrue();
    }

    @Test
    void wrongSecretCannotRotateAnExistingToken() {
        UUID userId = UUID.randomUUID();
        String oldTokenId = UUID.randomUUID().toString();
        store.saveRefreshToken(userId, oldTokenId, "correct-secret", 1L);

        TokenStoreService.RotationResult result = store.rotateRefreshToken(userId, oldTokenId,
                "wrong-secret", UUID.randomUUID().toString(), "new-secret", Instant.now(), 1L);

        assertThat(result).isEqualTo(TokenStoreService.RotationResult.TOKEN_MISMATCH);
        assertThat(store.matchesRefreshToken(userId, oldTokenId, "correct-secret", 1L)).isTrue();
    }
}
