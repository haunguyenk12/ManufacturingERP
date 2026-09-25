package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.audit.model.AuditChangeMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit logging via AOP.
 *
 * <p>This annotation is the fast path for ordinary CRUD, which is most of the 120-odd audited methods
 * in this codebase. It is deliberately <em>not</em> stretched to describe every command: anything that
 * touches several entities, or whose meaningful change lives in a child collection, is described by an
 * {@code AuditDescriptorProvider} / {@code AuditChangeProvider} instead. Piling more heuristics into
 * the aspect is how the previous version ended up guessing entity ids from argument types and
 * inferring CREATE/UPDATE/DELETE from the suffix of an enum constant.
 *
 * <h2>Expression context (AR-4)</h2>
 * The v2 attributes ({@link #entityId()}, {@link #entityName()}, {@link #companyId()},
 * {@link #plantId()}, {@link #warehouseId()}) are SpEL evaluated against a context that exposes:
 * <ul>
 *   <li>named method arguments — {@code #workOrderId}, {@code #request}</li>
 *   <li>{@code #args[0]} positional access</li>
 *   <li>{@code #result} — the return value, {@code null} for a {@code void} method</li>
 *   <li>{@code #exception} — only on the failure path</li>
 * </ul>
 * Expressions are parsed once per method and cached. A malformed expression fails loudly at first use
 * with the offending method named, rather than logging at debug level and yielding {@code null} —
 * which is how a whole class of events quietly lost its entity id.
 *
 * <pre>{@code
 * @Auditable(action = AuditAction.WORK_ORDER_RELEASED,
 *            entityType = "WorkOrder",
 *            entityId = "#workOrderId",              // works even though the method returns void
 *            entityName = "#result?.workOrderNo",
 *            plantId = "#result?.plantId",
 *            changeMode = AuditChangeMode.NONE)
 * public void release(UUID workOrderId) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** The audit action to record. */
    AuditAction action();

    /** Java class name of the entity being acted upon (empty = audit-level only). */
    String entityType() default "";

    /**
     * <strong>Legacy.</strong> SpEL evaluated with the return value as the root object, e.g.
     * {@code "id.toString()"}. Kept because ~120 call sites use it and rewriting them all in one
     * change would be a large untested diff. It cannot see method arguments, which is precisely why
     * every {@code void} method annotated this way records a {@code null} entity id — use
     * {@link #entityId()} for new code.
     */
    String entityIdExpression() default "";

    /** SpEL over the full invocation context. Takes precedence over {@link #entityIdExpression()}. */
    String entityId() default "";

    /** SpEL for the human-readable label; falls back to the automatic name resolver when empty. */
    String entityName() default "";

    /** SpEL yielding a {@code UUID} (or its text form) for the owning company. */
    String companyId() default "";

    /**
     * SpEL yielding a {@code UUID} (or its text form) for the plant the change landed in. This is
     * what finally populates {@code audit_logs.plant_id}, whose filter has returned nothing since the
     * column was added because no writer ever set it.
     */
    String plantId() default "";

    /** SpEL yielding a {@code UUID} (or its text form) for the warehouse. */
    String warehouseId() default "";

    /**
     * How to produce field-level changes. {@link AuditChangeMode#AUTO} diffs scalar fields only and
     * makes no claim about collections; use {@link AuditChangeMode#CUSTOM} with a provider for
     * aggregates whose real change is in a child collection.
     */
    AuditChangeMode changeMode() default AuditChangeMode.AUTO;

    /**
     * Create/update/delete semantics of this command, declared instead of guessed from the spelling
     * of the action constant. Left {@link com.erp.manufacturing.common.audit.model.AuditOperation#INFERRED}
     * for call sites not yet migrated.
     */
    com.erp.manufacturing.common.audit.model.AuditOperation operation()
            default com.erp.manufacturing.common.audit.model.AuditOperation.INFERRED;

    /**
     * Stable machine-readable reason for the success record. Failures derive theirs from the thrown
     * {@code AppException}'s {@code ErrorCode}, never from the exception message.
     */
    String reasonCode() default "";
}
