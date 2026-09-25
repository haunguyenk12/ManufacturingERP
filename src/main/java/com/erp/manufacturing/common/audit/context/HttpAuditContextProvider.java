package com.erp.manufacturing.common.audit.context;

import com.erp.manufacturing.common.audit.AuditInputSanitizer;
import com.erp.manufacturing.common.audit.model.AuditActorSnapshot;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.model.AuditSource;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Reads the audit context off the current servlet request (AR-3).
 *
 * <p>Two differences from the old {@code RequestContext.capture(request)}:
 *
 * <ol>
 *   <li>It uses {@link RequestContextHolder#getRequestAttributes()}, which returns {@code null} off a
 *       request thread, instead of {@code currentRequestAttributes()}, which throws. Absence of a
 *       request is a normal condition here, not an error — it just means another provider answers.</li>
 *   <li>Every value is passed through {@link AuditInputSanitizer} at capture time, so a caller-supplied
 *       {@code User-Agent} or {@code X-Trace-Id} is already within column limits before it can reach
 *       an INSERT.</li>
 * </ol>
 *
 * <p>The scope is left empty on purpose. An authenticated principal knows which company or plant it
 * <em>may</em> touch, which is not the same as where a given change landed; filling {@code plantId}
 * from the session would make the read-API filter confidently wrong. Scope is supplied by the command
 * that actually knows, and merged in by {@code AuditRecorder}.
 */
@Component
@RequiredArgsConstructor
public class HttpAuditContextProvider implements AuditContextProvider {

    private final AuditInputSanitizer sanitizer;

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Optional<AuditContext> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return Optional.of(new AuditContext(
                currentActor(),
                new AuditRequestSnapshot(
                        sanitizer.traceId(resolveTraceId(request)),
                        sanitizer.clientIp((String) request.getAttribute("clientIp")),
                        sanitizer.userAgent(request.getHeader("User-Agent")),
                        sanitizer.httpMethod(request.getMethod()),
                        sanitizer.requestPath(request.getRequestURI())),
                AuditScope.EMPTY,
                AuditSource.HTTP));
    }

    /** The MDC copy is authoritative; the request attribute is the fallback for async dispatches. */
    private String resolveTraceId(HttpServletRequest request) {
        String fromMdc = MDC.get("traceId");
        return fromMdc != null ? fromMdc : (String) request.getAttribute("traceId");
    }

    private AuditActorSnapshot currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return AuditActorSnapshot.user(principal.getUserId(),
                    sanitizer.username(principal.getUsername()));
        }
        return new AuditActorSnapshot(null, null, AuditActorSnapshot.AuditActorType.USER);
    }
}
