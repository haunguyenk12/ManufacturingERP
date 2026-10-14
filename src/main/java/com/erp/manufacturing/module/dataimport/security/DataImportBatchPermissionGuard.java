package com.erp.manufacturing.module.dataimport.security;

import com.erp.manufacturing.module.dataimport.domain.DataImportKind;
import com.erp.manufacturing.module.dataimport.domain.ImportRun;
import com.erp.manufacturing.module.dataimport.domain.ImportRunKind;
import com.erp.manufacturing.module.dataimport.repository.ImportRunRepository;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authorisation of the fixed-template import path (G3, NEXT_PHASE_PLAN.md §10.2 D3/D7).
 *
 * <p>A batch of kind K needs <em>both</em> K's READ and K's MANAGE on the batch scope — the PLANT for
 * items (a COMPANY or GLOBAL assignment covering it also counts, FE Q2), the COMPANY for suppliers and
 * customers. The pair is always taken from the same catalogue, so READ of one and MANAGE of another
 * never adds up to an import. It is checked on every call, including the replay of a committed batch,
 * so a revoked assignment stops the user from reading the receipt too (FE A21).
 */
@Component("dataImportBatchGuard")
@RequiredArgsConstructor
public class DataImportBatchPermissionGuard {

    private final PermissionGuard permissionGuard;
    private final ImportRunRepository runRepository;

    public boolean canImport(Authentication authentication, DataImportKind kind, UUID companyId, UUID plantId) {
        if (kind == null) {
            return false;
        }
        if (kind.plantScoped()) {
            return plantId != null && hasPair(authentication, kind, "PLANT", plantId);
        }
        return companyId != null && hasPair(authentication, kind, "COMPANY", companyId);
    }

    /** One explicit kind ⇒ that kind's pair; no kind ⇒ at least one catalogue the caller may import. */
    public boolean canList(Authentication authentication, DataImportKind kind, UUID companyId, UUID plantId) {
        return kind != null
                ? canImport(authentication, kind, companyId, plantId)
                : !allowedKinds(authentication, companyId, plantId).isEmpty();
    }

    /** The catalogues whose batches the caller may see in this company (items only with a plant). */
    public Set<DataImportKind> allowedKinds(Authentication authentication, UUID companyId, UUID plantId) {
        Set<DataImportKind> allowed = EnumSet.noneOf(DataImportKind.class);
        for (DataImportKind kind : DataImportKind.values()) {
            if (canImport(authentication, kind, companyId, plantId)) {
                allowed.add(kind);
            }
        }
        return allowed;
    }

    /**
     * Deliberately answers {@code true} when the batch does not exist or belongs to someone else: the
     * service then answers 404 for both, so a batch id never reveals whether another user's batch
     * exists (FE §1.4). Only the owner's own batch is judged on permissions — and answered 403 when
     * they no longer hold the pair.
     */
    @Transactional(readOnly = true)
    public boolean canAccessBatch(Authentication authentication, UUID batchId) {
        Optional<UUID> caller = principalUserId(authentication);
        Optional<ImportRun> batch = batchId == null
                ? Optional.empty()
                : runRepository.findByImportRunIdAndKind(batchId, ImportRunKind.FIXED_TEMPLATE);
        if (caller.isEmpty() || batch.isEmpty() || !caller.get().equals(batch.get().getOwnerUserId())) {
            return caller.isPresent();
        }
        ImportRun run = batch.get();
        return DataImportKind.fromTargetType(run.getTargetType())
                .map(kind -> canImport(authentication, kind, run.getCompany().getCompanyId(),
                        run.getPlant() == null ? null : run.getPlant().getPlantId()))
                .orElse(false);
    }

    private boolean hasPair(Authentication authentication, DataImportKind kind, String scopeType, UUID scopeId) {
        return permissionGuard.hasResourceAccess(authentication, kind.readPermission(), scopeType, scopeId)
                && permissionGuard.hasResourceAccess(authentication, kind.managePermission(), scopeType, scopeId);
    }

    private Optional<UUID> principalUserId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.ofNullable(principal.getUserId());
        }
        return Optional.empty();
    }
}
