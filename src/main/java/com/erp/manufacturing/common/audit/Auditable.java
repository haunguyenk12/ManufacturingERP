package com.erp.manufacturing.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit logging via AOP.
 * The annotated method will have an {@link AuditLogEvent} published after successful execution.
 *
 * <pre>{@code
 * @Auditable(action = AuditAction.WORK_ORDER_CREATED,
 *            entityType = "WorkOrder",
 *            entityIdExpression = "id.toString()")
 * public WorkOrderResponse create(WorkOrderRequest req) { ... }
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
     * SpEL expression evaluated on the method's return value to extract the entity's ID.
     * Example: {@code "id.toString()"} for a DTO with an {@code id} field.
     * Leave empty to skip entity ID extraction.
     */
    String entityIdExpression() default "";
}
