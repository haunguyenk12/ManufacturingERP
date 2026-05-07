package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP aspect that intercepts methods annotated with {@link Auditable}.
 * Publishes an {@link AuditLogEvent} via {@link AuditLogService} after execution.
 * On exception, publishes a FAILURE event and re-throws.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditableAspect {

    private final AuditLogService  auditLogService;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            try {
                auditLogService.logFailure(
                        RequestContext.capture(currentRequest()),
                        auditable.action(),
                        t.getMessage());
            } catch (Exception auditEx) {
                log.error("[Audit] Failed to log failure event", auditEx);
            }
            throw t;
        }

        try {
            String entityId = resolveEntityId(auditable.entityIdExpression(), result);
            String entityType = auditable.entityType().isBlank() ? null : auditable.entityType();
            auditLogService.logEntity(
                    RequestContext.capture(currentRequest()),
                    auditable.action(),
                    entityType,
                    entityId);
        } catch (Exception auditEx) {
            log.error("[Audit] Failed to log success event", auditEx);
        }

        return result;
    }

    private String resolveEntityId(String expression, Object result) {
        if (expression == null || expression.isBlank() || result == null) return null;
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext(result);
            return spelParser.parseExpression(expression).getValue(ctx, String.class);
        } catch (Exception e) {
            log.debug("[Audit] SpEL expression '{}' evaluation failed: {}", expression, e.getMessage());
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest();
    }
}
