package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.model.AuditChangeMode;
import com.erp.manufacturing.common.audit.model.AuditEntityRef;
import com.erp.manufacturing.common.audit.model.AuditOutcome;
import com.erp.manufacturing.common.audit.model.AuditRecordDraft;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.spi.AuditChangeProvider;
import com.erp.manufacturing.common.audit.spi.AuditDescriptorProvider;
import com.erp.manufacturing.common.audit.spi.AuditInvocation;
import com.erp.manufacturing.common.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Intercepts {@link Auditable} methods and records the outcome through {@link AuditRecorder}.
 *
 * <h2>Ordering</h2>
 * This aspect must run <em>inside</em> the transaction interceptor: the success record is an outbox
 * INSERT that has to join the business transaction, so the transaction must already be open when the
 * aspect runs and must not commit until after it. Running outside would put that INSERT in a
 * transaction of its own and silently restore the atomicity gap the outbox exists to close.
 *
 * <p>Both orders are therefore pinned explicitly ({@link #TRANSACTION_ADVISOR_ORDER} in
 * {@code AuditConfig}, {@link #AUDIT_ASPECT_ORDER} here) rather than left at the shared default,
 * where the outcome would depend on registration order. {@code AuditPipelineIT} asserts the effect on
 * a real transaction instead of trusting this comment.
 *
 * <h2>Failure path</h2>
 * The failure record goes through {@code recordFailure}, which writes in a {@code REQUIRES_NEW}
 * transaction, so it survives the rollback that is about to happen. Its reason comes from the
 * {@code AppException}'s {@code ErrorCode} — never from {@code Throwable.getMessage()}, which is
 * free-form, often quotes the offending data, and is not something to copy into a table that is meant
 * to be safe to read.
 */
@Aspect
@Component
@Order(AuditableAspect.AUDIT_ASPECT_ORDER)
@Slf4j
public class AuditableAspect {

    /**
     * Order of Spring's transaction advisor, set explicitly by {@code AuditConfig}.
     *
     * <p>It defaults to {@link Ordered#LOWEST_PRECEDENCE}, which leaves no value this aspect could
     * take to be reliably <em>inside</em> it — equal orders resolve arbitrarily, so the atomicity of
     * the outbox insert would depend on bean registration order. Pinning both is what turns that from
     * luck into a guarantee.
     */
    public static final int TRANSACTION_ADVISOR_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /** Higher than the transaction advisor, therefore nested inside it. */
    public static final int AUDIT_ASPECT_ORDER = Ordered.LOWEST_PRECEDENCE - 50;

    private final AuditRecorder auditRecorder;
    private final AuditChangeCaptureService changeCaptureService;
    private final AuditExpressionEvaluator expressionEvaluator;
    private final List<AuditDescriptorProvider> descriptorProviders;
    private final List<AuditChangeProvider> changeProviders;

    public AuditableAspect(AuditRecorder auditRecorder,
                           AuditChangeCaptureService changeCaptureService,
                           AuditExpressionEvaluator expressionEvaluator,
                           List<AuditDescriptorProvider> descriptorProviders,
                           List<AuditChangeProvider> changeProviders) {
        this.auditRecorder = auditRecorder;
        this.changeCaptureService = changeCaptureService;
        this.expressionEvaluator = expressionEvaluator;
        this.descriptorProviders = descriptorProviders.stream()
                .sorted(Comparator.comparingInt(AuditDescriptorProvider::order)).toList();
        this.changeProviders = changeProviders.stream()
                .sorted(Comparator.comparingInt(AuditChangeProvider::order)).toList();
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Object[] arguments = pjp.getArgs();
        String[] argumentNames = signature.getParameterNames();

        Map<String, JsonNode> before = captureBefore(auditable, arguments);
        AuditInvocation preInvocation =
                AuditInvocation.before(method, arguments, argumentNames, auditable, before);
        Object preState = capturePreState(preInvocation);

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable failure) {
            recordFailure(preInvocation.withOutcome(null, failure, preState));
            throw failure;
        }

        recordSuccess(preInvocation.withOutcome(result, null, preState));
        return result;
    }

    /**
     * Lets the descriptor that will describe this call record anything that becomes unrecoverable once
     * the method returns. Failures here are swallowed: a provider that cannot read pre-state must not
     * be able to abort the business command it was only observing.
     */
    private Object capturePreState(AuditInvocation invocation) {
        try {
            return descriptorProviders.stream()
                    .filter(provider -> provider.supports(invocation))
                    .findFirst()
                    .map(provider -> provider.capturePreState(invocation))
                    .orElse(null);
        } catch (Exception e) {
            log.error("[Audit] Pre-state capture failed for {} error={}",
                    invocation.method().getName(), e.getClass().getSimpleName(), e);
            return null;
        }
    }

    private void recordSuccess(AuditInvocation invocation) {
        try {
            Auditable auditable = invocation.auditable();
            AuditRecordDraft.Builder builder = auditRecorder.draft(auditable.action())
                    .outcome(AuditOutcome.SUCCESS)
                    .reasonCode(blankToNull(auditable.reasonCode()))
                    .primaryEntity(resolvePrimaryEntity(invocation))
                    .changes(resolveChanges(invocation));

            AuditScope scope = resolveScope(invocation);
            if (!scope.isEmpty()) {
                builder.scope(scope.mergedWith(auditRecorder.currentContext().scope()));
            }

            applyDescriptors(invocation, builder);
            auditRecorder.recordSuccess(builder.build());
        } catch (Exception e) {
            // FAIL_OPEN: an audit-side defect never changes the business outcome.
            log.error("[Audit] Could not describe success event for {} error={}",
                    invocation.method().getName(), e.getClass().getSimpleName(), e);
        }
    }

    private void recordFailure(AuditInvocation invocation) {
        try {
            Auditable auditable = invocation.auditable();
            AuditRecordDraft.Builder builder = auditRecorder.draft(auditable.action())
                    .outcome(AuditOutcome.FAILURE)
                    .reasonCode(reasonCodeOf(invocation.failure()))
                    .description(safeFailureDescription(invocation.failure()))
                    .primaryEntity(resolvePrimaryEntity(invocation));

            AuditScope scope = resolveScope(invocation);
            if (!scope.isEmpty()) {
                builder.scope(scope);
            }

            applyDescriptors(invocation, builder);
            auditRecorder.recordFailure(builder.build());
        } catch (Exception e) {
            log.error("[Audit] Could not describe failure event for {} error={}",
                    invocation.method().getName(), e.getClass().getSimpleName(), e);
        }
    }

    private void applyDescriptors(AuditInvocation invocation, AuditRecordDraft.Builder builder) {
        descriptorProviders.stream()
                .filter(provider -> provider.supports(invocation))
                .findFirst()
                .ifPresent(provider -> provider.describe(invocation, builder));
    }

    /**
     * Entity id resolution, in priority order: the v2 expression (which can see arguments), then the
     * legacy result-rooted expression, then the automatic name resolver for the label. The fallback
     * chain is what lets a {@code void} method finally name its target.
     */
    private AuditEntityRef resolvePrimaryEntity(AuditInvocation invocation) {
        Auditable auditable = invocation.auditable();
        String entityType = invocation.entityType();

        String entityId = expressionEvaluator.evaluateString(auditable.entityId(), invocation);
        if (entityId == null) {
            entityId = expressionEvaluator.evaluateOnResult(
                    auditable.entityIdExpression(), invocation.result());
        }

        String entityName = expressionEvaluator.evaluateString(auditable.entityName(), invocation);
        if (entityName == null) {
            entityName = changeCaptureService.resolveEntityName(
                    entityType, invocation.beforeSnapshot(), invocation.result());
        }

        if (entityType == null && entityId == null && entityName == null) {
            return null;
        }
        return AuditEntityRef.primary(entityType, entityId, entityName);
    }

    private AuditScope resolveScope(AuditInvocation invocation) {
        Auditable auditable = invocation.auditable();
        return AuditScope.of(
                expressionEvaluator.evaluateUuid(auditable.companyId(), invocation),
                expressionEvaluator.evaluateUuid(auditable.plantId(), invocation),
                expressionEvaluator.evaluateUuid(auditable.warehouseId(), invocation));
    }

    private List<AuditFieldChange> resolveChanges(AuditInvocation invocation) {
        AuditChangeMode mode = invocation.auditable().changeMode();
        if (mode == AuditChangeMode.NONE) {
            return List.of();
        }
        if (mode == AuditChangeMode.CUSTOM) {
            return changeProviders.stream()
                    .filter(provider -> provider.supports(invocation))
                    .findFirst()
                    .map(provider -> provider.capture(invocation))
                    .orElseGet(() -> {
                        log.warn("[Audit] {} declares CUSTOM change capture but no provider supports it",
                                invocation.method().getName());
                        return List.of();
                    });
        }
        // AUTO: a provider may still claim the method, which is how an aggregate gets a correct diff
        // without every call site having to be switched to CUSTOM by hand.
        return changeProviders.stream()
                .filter(provider -> provider.supports(invocation))
                .findFirst()
                .map(provider -> provider.capture(invocation))
                .orElseGet(() -> changeCaptureService.calculateChanges(
                        invocation.action(), invocation.auditable().operation(),
                        invocation.beforeSnapshot(), invocation.result()));
    }

    private Map<String, JsonNode> captureBefore(Auditable auditable, Object[] arguments) {
        if (auditable.changeMode() == AuditChangeMode.NONE) {
            return Map.of();
        }
        try {
            return changeCaptureService.captureBefore(
                    auditable.entityType(), arguments, auditable.action(), auditable.operation());
        } catch (Exception e) {
            log.error("[Audit] Could not snapshot entity state before {}",
                    auditable.action(), e);
            return Map.of();
        }
    }

    /** Uses the domain error code, which is stable and already published to clients. */
    private String reasonCodeOf(Throwable failure) {
        if (failure instanceof AppException appException) {
            return appException.getErrorCode().code();
        }
        return failure == null ? null : failure.getClass().getSimpleName();
    }

    /**
     * A short, non-quoting description. {@code AppException} messages are authored by us and already
     * travel to clients in the error envelope, so they are safe; anything else contributes only its
     * type, because an arbitrary exception message can carry the row that caused it.
     */
    private String safeFailureDescription(Throwable failure) {
        if (failure instanceof AppException appException) {
            return appException.getMessage();
        }
        return failure == null ? null : "Command failed with " + failure.getClass().getSimpleName();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
