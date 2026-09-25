package com.erp.manufacturing.common.audit.context;

import com.erp.manufacturing.common.audit.model.AuditActorSnapshot;
import com.erp.manufacturing.common.audit.model.AuditRequestSnapshot;
import com.erp.manufacturing.common.audit.model.AuditScope;
import com.erp.manufacturing.common.audit.model.AuditSource;

/**
 * Ambient facts an audit event inherits from whatever is executing: who is acting, over which
 * transport, in which scope.
 *
 * <p>It is captured on the originating thread and is immutable, so it can cross the async boundary
 * to the dispatcher without any of it being re-read from a {@code ThreadLocal} that no longer exists
 * there. That was the failure mode of the old {@code RequestContext.capture(request)} call inside the
 * aspect: correct on a request thread, an exception anywhere else.
 */
public record AuditContext(
        AuditActorSnapshot actor,
        AuditRequestSnapshot request,
        AuditScope scope,
        AuditSource source
) {

    public static AuditContext system(String componentName) {
        return new AuditContext(AuditActorSnapshot.system(componentName),
                AuditRequestSnapshot.EMPTY, AuditScope.EMPTY, AuditSource.SYSTEM);
    }

    public AuditContext withScope(AuditScope newScope) {
        return new AuditContext(actor, request, newScope, source);
    }
}
