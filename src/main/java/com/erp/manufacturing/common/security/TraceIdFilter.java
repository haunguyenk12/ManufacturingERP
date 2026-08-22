package com.erp.manufacturing.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * First filter in the chain. Sets traceId and clientIp in MDC and request attributes.
 * <p>
 * These values are consumed by:
 * <ul>
 *   <li>All log lines (via Logback MDC pattern)</li>
 *   <li>{@code RequestContext.capture()} for audit logging</li>
 *   <li>{@code RateLimitFilter} for IP-based rules</li>
 * </ul>
 * Supports distributed tracing: if {@code X-Trace-Id} header is provided, reuses it.
 */
@Component
@RequiredArgsConstructor
public class TraceIdFilter extends OncePerRequestFilter {

    private final IpExtractor ipExtractor;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        // Support distributed tracing – reuse upstream traceId if provided
        String traceId = Optional.ofNullable(request.getHeader("X-Trace-Id"))
                .filter(s -> s.matches("[A-Za-z0-9._-]{1,64}"))
                .orElse(UUID.randomUUID().toString().replace("-", "").substring(0, 16));

        String clientIp = ipExtractor.extract(request);

        MDC.put("traceId",  traceId);
        MDC.put("clientIp", clientIp);

        // Request attributes used downstream by RequestContext and RateLimitFilter
        request.setAttribute("traceId",  traceId);
        request.setAttribute("clientIp", clientIp);

        response.setHeader("X-Trace-Id", traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
