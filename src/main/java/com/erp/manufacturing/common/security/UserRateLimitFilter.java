package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.config.RateLimitProperties.Action;
import com.erp.manufacturing.config.RateLimitProperties.RateLimitRule;
import com.erp.manufacturing.config.RateLimitProperties.Scope;
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
 * USER-scope rate limiting filter. Runs AFTER {@link JwtAuthenticationFilter} so that
 * {@code authenticatedUserId} is guaranteed to be set in the request attribute.
 *
 * <p>Only evaluates rules with {@code scope: USER}. IP-scope rules are handled by
 * {@link RateLimitFilter} which runs before JWT authentication.
 *
 * <p>If the request is unauthenticated (no {@code authenticatedUserId}), this filter
 * is a no-op – anonymous requests are governed solely by IP-scope rules.
 *
 * <h3>Filter order</h3>
 * <pre>
 * TraceIdFilter (1) → RateLimitFilter/IP (2) → JwtAuthenticationFilter (3) → UserRateLimitFilter (4)
 * </pre>
 *
 * @see RateLimitFilter  IP-scope counterpart
 * @see RateLimitProperties
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties            rateLimitProperties;
    private final RedisTemplate<String, String>  redisTemplate;
    private final ObjectMapper                   objectMapper;
    private final AntPathMatcher                 pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        if (!rateLimitProperties.enabled()) {
            chain.doFilter(request, response);
            return;
        }

        // No authenticated user → skip; IP rules already applied upstream
        String userId = (String) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            if (isRefused(request, response, userId)) {
                return;
            }
        } catch (RuntimeException ex) {
            // EH-1 safety net - see JwtAuthenticationFilter for the full rationale. The rule
            // evaluation below talks to Redis, and an exception escaping a filter never reaches
            // GlobalExceptionHandler: the caller would get Spring Boot's default /error body instead
            // of the {code,result,message} envelope. The message is not echoed to the client.
            writeInternalError(response, request, ex);
            return;
        }

        chain.doFilter(request, response);
    }

    /** @return {@code true} when this filter has already written a refusal onto the response. */
    private boolean isRefused(HttpServletRequest request, HttpServletResponse response, String userId)
            throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<RateLimitResult> block = evaluateUser(path, userId);
        if (block.isPresent()) {
            RateLimitResult r = block.get();
            addRateLimitHeaders(response, r);
            writeError(response, 429, BusinessErrorCode.RATE_LIMIT_EXCEEDED,
                    "User rate limit exceeded. Retry after " + r.retryAfterSeconds() + "s.");
            return true;
        }
        return false;
    }

    // ── Core evaluation ──────────────────────────────────────────────────────

    private Optional<RateLimitResult> evaluateUser(String path, String userId) {
        return rateLimitProperties.rules().stream()
                .filter(r -> r.scope() == Scope.USER)
                .filter(r -> pathMatcher.match(r.pattern(), path))
                .map(r -> checkRule(r, userId))
                .filter(r -> !r.allowed())
                .findFirst();
    }

    private RateLimitResult checkRule(RateLimitRule rule, String identifier) {
        int limit      = getOverrideInt(rule.id(), "limit",         rule.limit());
        int windowSecs = getOverrideInt(rule.id(), "windowSeconds", rule.windowSeconds());

        long   epochWindow = Instant.now().getEpochSecond() / windowSecs;
        String key         = String.format("rate:counter:%s:%s:%d", rule.id(), identifier, epochWindow);

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSecs + 10L, TimeUnit.SECONDS);
        }
        long cnt = count != null ? count : 1;

        boolean allowed    = cnt <= limit;
        int     remaining  = (int) Math.max(0, limit - cnt);
        int     retryAfter = allowed ? 0 : (int)(windowSecs - Instant.now().getEpochSecond() % windowSecs);

        if (!allowed && rule.action() == Action.LOG_ONLY) {
            log.warn("[UserRateLimit][LOG_ONLY] rule={} userId={} count={}/{}", rule.id(), identifier, cnt, limit);
            return new RateLimitResult(true, rule.id(), limit, remaining, retryAfter);
        }
        return new RateLimitResult(allowed, rule.id(), limit, remaining, retryAfter);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    /** Same envelope the catch-all of {@code GlobalExceptionHandler} would have produced. */
    private void writeInternalError(HttpServletResponse response, HttpServletRequest request,
                                    RuntimeException ex) throws IOException {
        log.error("[{}] Unhandled exception in UserRateLimitFilter at {}: {}",
                BusinessErrorCode.INTERNAL_SERVER_ERROR.code(), request.getRequestURI(),
                ex.getMessage(), ex);
        writeError(response, BusinessErrorCode.INTERNAL_SERVER_ERROR.status().value(),
                BusinessErrorCode.INTERNAL_SERVER_ERROR,
                BusinessErrorCode.INTERNAL_SERVER_ERROR.message());
    }

    // ── Result record (shared shape with RateLimitFilter.RateLimitResult) ──

    public record RateLimitResult(
            boolean allowed,
            String  ruleId,
            int     limit,
            int     remaining,
            int     retryAfterSeconds
    ) {}
}
