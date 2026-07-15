package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BomLookupService {

    private final BomHeaderRepository bomHeaderRepository;

    @Transactional(readOnly = true)
    public Optional<BomHeader> findActiveBom(UUID companyId, UUID parentItemId) {
        return bomHeaderRepository.findWithLinesByCompanyCompanyIdAndParentItemItemIdAndStatus(
                companyId, parentItemId, BomStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public BomHeader getActiveBom(UUID companyId, UUID parentItemId) {
        return findActiveBom(companyId, parentItemId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Active BOM", parentItemId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, BomHeader> findActiveBoms(UUID companyId, Collection<UUID> parentItemIds) {
        if (companyId == null || parentItemIds == null || parentItemIds.isEmpty()) {
            return Map.of();
        }
        return bomHeaderRepository.findWithLinesByCompanyCompanyIdAndParentItemItemIdInAndStatus(
                        companyId, parentItemIds, BomStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        bom -> bom.getParentItem().getItemId(),
                        bom -> bom));
    }
}
