package com.erp.manufacturing.module.workcenter.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.repository.WorkCenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entry point for other modules that need Work Center master data (rule C7) — {@code routing} uses
 * it to resolve {@code RoutingOperationRequest.workCenterId} without touching
 * {@link WorkCenterRepository} directly. No {@code @PreAuthorize}: callers are already authorized on
 * their own aggregate, this is read-only master data lookup — same pattern as
 * {@code RoutingLookupService}/{@code BomLookupService}.
 */
@Service
@RequiredArgsConstructor
public class WorkCenterLookupService {

    private final WorkCenterRepository workCenterRepository;

    @Transactional(readOnly = true)
    public WorkCenter getActiveWorkCenter(UUID workCenterId) {
        WorkCenter workCenter = workCenterRepository.findById(workCenterId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work center", workCenterId));
        if (!workCenter.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive work center cannot be used: " + workCenterId);
        }
        return workCenter;
    }

    @Transactional(readOnly = true)
    public Map<String, WorkCenter> findWorkCentersByCode(UUID plantId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return workCenterRepository.findByPlantPlantIdAndCodeIn(plantId, codes).stream()
                .collect(Collectors.toMap(WorkCenter::getCode, Function.identity()));
    }
}
