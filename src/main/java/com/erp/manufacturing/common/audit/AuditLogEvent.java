package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.context.RequestContext;

/**
 * Spring application event carrying audit data.
 * Published on the request thread, consumed asynchronously by {@link AuditLogListener}.
 */
public record AuditLogEvent(
        RequestContext context,
        String         action,
        String         entityType,
        String         entityId,
        String         description,
        String         status
) {}
