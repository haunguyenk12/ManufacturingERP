package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import com.erp.manufacturing.module.workorder.domain.ProductionCostRate;
import com.erp.manufacturing.module.workorder.domain.WorkOrderCommand;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.ActorRef;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateCommandResult;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateRequest;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateResponse;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateRowResponse;
import com.erp.manufacturing.module.workorder.repository.ProductionCostRateRepository;
import com.erp.manufacturing.module.workorder.service.ReasonRules;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * G11a: hourly labor and overhead rates of work centers (FE C1, C2, C13). A rate belongs to the work center as a whole
 * (FE Q2), is saved as a new immutable version, and never changes a baseline already captured (FE R04, A03 — B256).
 *
 * <p>Reading the rate table needs the rate permission or a cost-view permission on the plant, and returns minimal work
 * center references only: a rate-only user is never handed the work center master (FE R28, Q30).
 */
@Service
@RequiredArgsConstructor
public class ProductionCostRateService {

    static final String READ_RATES = "@permissionGuard.hasResourceAccess(authentication, 'PERM_COST_RATE_MANAGE', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_WORK_ORDER_VARIANCE_READ', 'PLANT', #plantId)"
            + " or @permissionGuard.hasResourceAccess(authentication, 'PERM_PRODUCTION_COST_CALCULATE', 'PLANT', #plantId)";

    private final ProductionCostRateRepository rateRepository;
    private final WorkCenterLookupService workCenterLookupService;
    private final WorkOrderIdempotency idempotencyRecords;
    private final UserLookupService userLookupService;

    @Transactional(readOnly = true)
    @PreAuthorize(READ_RATES)
    public PageResult<RateRowResponse> list(UUID plantId, OrganizationStatus status, String search, Pageable pageable) {
        Page<WorkCenter> page = workCenterLookupService.searchWorkCenters(plantId, status,
                StringUtils.hasText(search) ? search.trim() : null, pageable);
        List<UUID> ids = page.getContent().stream().map(WorkCenter::getWorkCenterId).toList();
        Map<UUID, ProductionCostRate> current = currentRates(ids);
        Map<UUID, String> usernames = usernames(current.values().stream().map(ProductionCostRate::getCreatedBy));
        return PageResult.from(page.map(center -> {
            ProductionCostRate rate = current.get(center.getWorkCenterId());
            return new RateRowResponse(center.getWorkCenterId(), plantId, center.getCode(), center.getName(),
                    center.getStatus(), center.getCapacityUnits(), rate == null ? 0 : rate.getRateVersion(),
                    rate == null ? null : toResponse(rate, usernames));
        }));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(READ_RATES)
    public PageResult<RateResponse> history(UUID plantId, UUID workCenterId, Pageable pageable) {
        centerOfPlant(plantId, workCenterId);
        Page<ProductionCostRate> page = rateRepository.findByWorkCenterWorkCenterId(workCenterId, pageable);
        Map<UUID, String> usernames = usernames(page.getContent().stream().map(ProductionCostRate::getCreatedBy));
        return PageResult.from(page.map(rate -> toResponse(rate, usernames)));
    }

    /**
     * Saves version n+1. The work center must be ACTIVE and of the plant in the path; {@code expectedVersion} must be
     * the current version (two people saving on the same version: one wins, the other is told to reload). The
     * {@code UNIQUE (work_center_id, rate_version)} constraint is what decides two that pass this check together.
     */
    @Transactional
    @PreAuthorize("@permissionGuard.hasResourceAccess(authentication, 'PERM_COST_RATE_MANAGE', 'PLANT', #plantId)")
    @Auditable(action = AuditAction.PRODUCTION_COST_RATE_SET, entityType = "WorkCenter",
            entityIdExpression = "#workCenterId.toString()", plantId = "#plantId")
    public RateCommandResult save(UUID plantId, UUID workCenterId, RateRequest request, String rawKey) {
        String key = idempotencyRecords.requiredKey(rawKey);
        WorkOrderIdempotencyFingerprint fingerprint = new WorkOrderIdempotencyFingerprint(plantId, workCenterId, request);
        if (idempotencyRecords.replay(key, WorkOrderCommand.COST_RATE_SET, fingerprint).isPresent()) {
            ProductionCostRate latest = rateRepository.findTopByWorkCenterWorkCenterIdOrderByRateVersionDesc(workCenterId)
                    .orElseThrow();
            return new RateCommandResult(workCenterId, latest.getRateVersion());
        }
        String reason = ReasonRules.lengthChecked(request.reason(), "reason");
        if (reason == null) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("reason", "must not be blank"));
        }
        if (StringUtils.hasText(request.currency()) && !ProductionCostRate.CURRENCY.equals(request.currency().trim())) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("currency", "must be VND"));
        }
        WorkCenter center = centerOfPlant(plantId, workCenterId);
        if (!center.isActive()) {
            throw ExceptionFactory.custom(BusinessErrorCode.RESOURCE_INACTIVE, "Work center is inactive: " + center.getCode());
        }
        int currentVersion = rateRepository.findTopByWorkCenterWorkCenterIdOrderByRateVersionDesc(workCenterId)
                .map(ProductionCostRate::getRateVersion).orElse(0);
        if (!Objects.equals(request.expectedVersion(), currentVersion)) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.CONCURRENT_MODIFICATION, Map.of("expectedVersion",
                    "rate version is " + currentVersion + ", not " + request.expectedVersion() + "; reload it"));
        }
        idempotencyRecords.record(key, WorkOrderCommand.COST_RATE_SET, workCenterId, fingerprint);
        ProductionCostRate saved = rateRepository.saveAndFlush(ProductionCostRate.builder()
                .workCenter(center)
                .plantId(plantId)
                .rateVersion(currentVersion + 1)
                .laborRate(CostMath.c6(request.laborRate()))
                .overheadRate(CostMath.c6(request.overheadRate()))
                .reason(reason)
                .build());
        return new RateCommandResult(workCenterId, saved.getRateVersion());
    }

    /** The current rate of each work center that has one. No authorisation: the caller is (release, costing). */
    @Transactional(readOnly = true)
    public Map<UUID, ProductionCostRate> currentRates(Collection<UUID> workCenterIds) {
        if (workCenterIds.isEmpty()) {
            return Map.of();
        }
        return rateRepository.findCurrentByWorkCenterIds(workCenterIds).stream()
                .collect(Collectors.toMap(rate -> rate.getWorkCenter().getWorkCenterId(), Function.identity()));
    }

    /** A work center of another plant is answered as not found (no existence leak across plants). */
    private WorkCenter centerOfPlant(UUID plantId, UUID workCenterId) {
        return workCenterLookupService.findWorkCenter(workCenterId)
                .filter(center -> center.getPlant().getPlantId().equals(plantId))
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Work center", workCenterId));
    }

    private Map<UUID, String> usernames(Stream<UUID> ids) {
        List<UUID> distinct = ids.filter(Objects::nonNull).distinct().toList();
        return distinct.isEmpty() ? Map.of() : userLookupService.findUsernames(distinct);
    }

    static RateResponse toResponse(ProductionCostRate rate, Map<UUID, String> usernames) {
        UUID actor = rate.getCreatedBy();
        return new RateResponse(rate.getRateId(), rate.getRateVersion(), rate.getLaborRate(), rate.getOverheadRate(),
                rate.getCurrency(), rate.getReason(), actor == null ? null : new ActorRef(actor, usernames.get(actor)),
                rate.getCreatedAt());
    }

    /** What a rate key fingerprints: the plant and work center of the path plus the body. */
    record WorkOrderIdempotencyFingerprint(UUID plantId, UUID workCenterId, Object body) {
    }
}
