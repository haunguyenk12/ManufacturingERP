package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.config.RateLimitProperties.Scope;
import com.erp.manufacturing.config.RateLimitProperties.Action;
import com.erp.manufacturing.config.RateLimitProperties.RateLimitRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Config-driven rate limiting filter.
 * <p>
 * Rules are loaded from {@code app.rate-limit.rules} in YAML.
 * Runtime overrides are read from Redis: {@code rate:rule:override:{ruleId}}.
 * Whitelisted IPs skip all checks: {@code rate:whitelist:ip:{ip}}.
 * Blacklisted IPs are blocked with 403: {@code rate:blacklist:ip:{ip}}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties    rateLimitProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final IpExtractor            ipExtractor;
    private final ObjectMapper           objectMapper;
    private final AntPathMatcher         pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        if (!rateLimitProperties.enabled()) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = ipExtractor.extract(request);
        String path     = request.getRequestURI();

        // ── Blacklist check ─────────────────────────────────────────────
        String blacklistReason = redisTemplate.opsForValue().get("rate:blacklist:ip:" + clientIp);
        if (blacklistReason != null) {
            writeError(response, 403, ErrorCode.ACCESS_DENIED, "IP blocked: " + blacklistReason);
            return;
        }

        // ── IP-scope rules ──────────────────────────────────────────────
        Optional<RateLimitResult> ipBlock = evaluate(path, Scope.IP, clientIp);
        if (ipBlock.isPresent()) {
            addRateLimitHeaders(response, ipBlock.get());
            writeError(response, 429, ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many requests. Retry after " + ipBlock.get().retryAfterSeconds() + "s.");
            return;
        }

        // ── USER-scope rules (after JwtFilter has set authenticatedUserId) ──
        String userId = (String) request.getAttribute("authenticatedUserId");
        if (userId != null) {
            Optional<RateLimitResult> userBlock = evaluate(path, Scope.USER, userId);
            if (userBlock.isPresent()) {
                addRateLimitHeaders(response, userBlock.get());
                writeError(response, 429, ErrorCode.RATE_LIMIT_EXCEEDED, "User rate limit exceeded.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    // ── Core evaluation ─────────────────────────────────────────────────────

    private Optional<RateLimitResult> evaluate(String path, Scope scope, String identifier) {
        // Whitelist bypass (IP only)
        if (scope == Scope.IP && isWhitelisted(identifier)) {
            return Optional.empty();
        }

        return rateLimitProperties.rules().stream()
                .filter(r -> r.scope() == scope)
                .filter(r -> pathMatcher.match(r.pattern(), path))
                .map(r -> checkRule(r, identifier))
                .filter(r -> !r.allowed())
                .findFirst();
    }

    private RateLimitResult checkRule(RateLimitRule rule, String identifier) {
        int limit      = getOverrideInt(rule.id(), "limit",         rule.limit());
        int windowSecs = getOverrideInt(rule.id(), "windowSeconds", rule.windowSeconds());

        long epochWindow = Instant.now().getEpochSecond() / windowSecs;
        String key = String.format("rate:counter:%s:%s:%d", rule.id(), identifier, epochWindow);

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSecs + 10L, TimeUnit.SECONDS);
        }
        long cnt = count != null ? count : 1;

        boolean allowed    = cnt <= limit;
        int     remaining  = (int) Math.max(0, limit - cnt);
        int     retryAfter = allowed ? 0 : (int)(windowSecs - Instant.now().getEpochSecond() % windowSecs);

        if (!allowed && rule.action() == Action.LOG_ONLY) {
            log.warn("[RateLimit][LOG_ONLY] rule={} id={} count={}/{}", rule.id(), identifier, cnt, limit);
            return new RateLimitResult(true, rule.id(), limit, remaining, retryAfter);
        }
        return new RateLimitResult(allowed, rule.id(), limit, remaining, retryAfter);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private boolean isWhitelisted(String ip) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("rate:whitelist:ip:" + ip));
    }

    private int getOverrideInt(String ruleId, String field, int defaultVal) {
        try {
            Object raw = redisTemplate.opsForHash().get("rate:rule:override:" + ruleId, field);
            return raw != null ? Integer.parseInt(raw.toString()) : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult r) {
        response.setHeader("X-RateLimit-Limit",     String.valueOf(r.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(r.remaining()));
        response.setHeader("X-RateLimit-Rule",      r.ruleId());
        if (!r.allowed()) {
            response.setHeader("Retry-After", String.valueOf(r.retryAfterSeconds()));
        }
    }

    private void writeError(HttpServletResponse response, int status,
                             ErrorCode code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }

    // ── Result record ─────────────────────────────────────────────────────

    public record RateLimitResult(
            boolean allowed,
            String  ruleId,
            int     limit,
            int     remaining,
            int     retryAfterSeconds
    ) {}
}
