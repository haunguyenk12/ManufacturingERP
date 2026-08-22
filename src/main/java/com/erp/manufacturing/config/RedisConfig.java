package com.erp.manufacturing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration with String serializers for both key and value.
 * All values stored are plain strings (counts, tokens, IPs).
 *
 * <p>🔴 <b>Never declare an {@code @Bean ObjectMapper} here.</b> Boot's {@code JacksonAutoConfiguration}
 * is {@code @ConditionalOnMissingBean}, so any {@code ObjectMapper} bean — even one meant only for
 * Redis — silently becomes <i>the</i> mapper of the whole web layer and every {@code spring.jackson.*}
 * property in {@code application.yml} stops applying. That is exactly what happened until 2026-08-12:
 * {@code write-dates-as-timestamps: false} was ignored, so {@code LocalDate} went out as
 * {@code [2026,8,15]} and {@code Instant} as an epoch number while
 * {@code docs/api-guide-for-frontend.md §2.6} promised ISO strings to the frontend (debt #27).
 * The template above serialises with {@link StringRedisSerializer} only and needs no mapper at all.
 * If Redis ever has to store JSON, build a mapper <i>locally</i> inside that serializer — do not
 * expose it as a bean.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
