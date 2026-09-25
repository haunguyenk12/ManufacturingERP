package com.erp.manufacturing.common.audit.spi;

import com.erp.manufacturing.common.audit.model.AuditRecordDraft;

/**
 * Describes an audited command that the annotation cannot express on its own (AR-4).
 *
 * <p>The extension point exists so that multi-entity commands stop being approximated. A grant of a
 * permission to a role touches two objects; an assignment touches three. The annotation can name one.
 * Rather than growing more annotation attributes for each shape, a provider gets the whole invocation
 * and fills in the draft directly.
 *
 * <p>Providers run after the annotation defaults have been applied, so they refine rather than
 * replace: setting a primary target overrides the annotation's, and related targets add to it.
 */
public interface AuditDescriptorProvider {

    boolean supports(AuditInvocation invocation);

    void describe(AuditInvocation invocation, AuditRecordDraft.Builder builder);

    /**
     * Optionally records state <em>before</em> the command runs, handed back on
     * {@link AuditInvocation#preState()}.
     *
     * <p>Needed for commands whose outcome cannot be reconstructed afterwards. Granting a permission
     * that a role already holds and granting one it did not are indistinguishable once the method has
     * returned: both end with the grant present, and the method returns {@code void}. Without a
     * pre-state hook the audit trail has to claim a change happened, which is a false statement in
     * half the cases.
     */
    default Object capturePreState(AuditInvocation invocation) {
        return null;
    }

    /** Lower runs first; the first supporting provider wins. */
    default int order() {
        return 100;
    }
}
