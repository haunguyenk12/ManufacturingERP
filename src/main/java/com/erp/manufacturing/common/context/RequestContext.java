package com.erp.manufacturing.common.context;

import com.erp.manufacturing.module.user.domain.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Immutable snapshot of the current request context.
 * <p>
 * Captured on the request thread – passed into {@code AuditLogEvent} so that
 * async threads can access user/IP/traceId without holding onto ThreadLocal references.
 */
public record RequestContext(
        UUID   userId,
        String username,
        String clientIp,
        String userAgent,
        String traceId
) {

    /**
     * Captures RequestContext from current SecurityContext + request attributes set by filters.
     * Must be called on the request-processing thread.
     */
    public static RequestContext capture(HttpServletRequest request) {
        UUID   userId   = null;
        String username = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal principal) {
            userId   = principal.getUserId();
            username = principal.getUsername();
        }

        return new RequestContext(
                userId,
                username,
                (String) request.getAttribute("clientIp"),
                request.getHeader("User-Agent"),
                MDC.get("traceId")
        );
    }

    /** Creates a context for auth events that happen before authentication (e.g. failed login). */
    public static RequestContext forAuth(UUID userId, String username, String ip, String traceId) {
        return new RequestContext(userId, username, ip, null, traceId);
    }
}
