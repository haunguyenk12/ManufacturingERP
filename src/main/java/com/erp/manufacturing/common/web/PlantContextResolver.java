package com.erp.manufacturing.common.web;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Validates the {@code X-Plant-Id} header against the plant a request already names in its path,
 * query or body.
 *
 * <p>The header is a <b>cross-check, not a substitute</b>: every plant-scoped endpoint still carries
 * {@code plantId} explicitly, because that is what {@code @PreAuthorize} evaluates
 * ({@code PermissionGuard.hasResourceAccess(..., 'PLANT', plantId)}). Treating the header as the
 * source of truth would move the authorisation input outside the method-security expression.
 *
 * <p>An absent header is allowed so existing callers keep working; a header that disagrees with the
 * request is a client bug and fails loudly rather than silently acting on the wrong plant.
 *
 * <p><b>Where this applies (settled in {@code D1}, 2026-07-28).</b> The cross-check is only
 * meaningful on endpoints that <em>name a plant explicitly</em> — {@code /plants/{plantId}/...} or a
 * {@code plantId} in the query/body. Endpoints identified by an aggregate instead
 * ({@code /work-orders/{workOrderId}/...}, {@code /sales-orders/{id}/...}) derive their plant from
 * that aggregate: the id <em>is</em> the scope, there is no second value for the header to disagree
 * with, and {@code @PreAuthorize} already denies access to a work order in another plant. Wiring the
 * header into those endpoints would add a check with nothing to check.
 *
 * <p>🔴 <b>The boundary is per endpoint, not per controller</b> (restated in {@code F7}, widened in
 * {@code F8}). {@code ManufacturingExecutionController} hosts both kinds: its candidate screens and
 * flat plant-scoped lists ({@code /production-executions/candidates},
 * {@code /production-receipts/candidates}, {@code /production-receipts},
 * {@code /material-issues}) <em>do</em> cross-check, while everything under
 * {@code /work-orders/{id}/...} and the flat
 * {@code GET /production-executions?workOrderId=} deliberately do not. When adding an endpoint, ask
 * <em>"does this endpoint have two sources of plant?"</em> — never <em>"does this controller take
 * the header?"</em>. Both sides are pinned by tests in {@code ManufacturingExecutionControllerTest}.
 */
@Component
public class PlantContextResolver {

    public static final String HEADER = "X-Plant-Id";

    public void ensureMatches(String headerValue, UUID requestPlantId) {
        if (!StringUtils.hasText(headerValue)) {
            return;
        }
        UUID headerPlantId;
        try {
            headerPlantId = UUID.fromString(headerValue.trim());
        } catch (IllegalArgumentException e) {
            throw ExceptionFactory.custom(ValidationErrorCode.FIELD_FORMAT_INVALID,
                    HEADER + " must be a valid UUID");
        }
        if (requestPlantId != null && !headerPlantId.equals(requestPlantId)) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    HEADER + " does not match the plant referenced by this request");
        }
    }
}
