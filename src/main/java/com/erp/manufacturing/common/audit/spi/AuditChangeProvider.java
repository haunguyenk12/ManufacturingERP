package com.erp.manufacturing.common.audit.spi;

import com.erp.manufacturing.common.audit.AuditFieldChange;

import java.util.List;

/**
 * Produces field-level changes for an aggregate the automatic differ cannot handle (AR-4).
 *
 * <p>The automatic differ intersects a JPA entity snapshot taken before the call with the response
 * DTO returned after it. Those two shapes only overlap on scalar fields, so for anything whose real
 * change is a child collection the honest answer is "this differ cannot tell you" — and reporting
 * "no changes" for a BOM line replacement is worse than reporting nothing, because it reads as
 * evidence that nothing changed.
 */
public interface AuditChangeProvider {

    boolean supports(AuditInvocation invocation);

    List<AuditFieldChange> capture(AuditInvocation invocation);

    default int order() {
        return 100;
    }
}
