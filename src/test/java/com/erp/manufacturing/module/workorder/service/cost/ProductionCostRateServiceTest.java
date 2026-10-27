package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import com.erp.manufacturing.module.workorder.domain.ProductionCostRate;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateCommandResult;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateRequest;
import com.erp.manufacturing.module.workorder.repository.ProductionCostRateRepository;
import com.erp.manufacturing.module.workorder.repository.WorkOrderIdempotencyRecordRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** G11a: saving an hourly rate writes version n+1, after every check, and only then (FE R03, R04). */
@ExtendWith(MockitoExtension.class)
class ProductionCostRateServiceTest {

    @Mock ProductionCostRateRepository rateRepository;
    @Mock WorkCenterLookupService workCenterLookupService;
    @Mock WorkOrderIdempotencyRecordRepository idempotencyRecordRepository;
    @Mock UserLookupService userLookupService;

    private ProductionCostRateService service;
    private final UUID plantId = UUID.randomUUID();
    private final UUID workCenterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductionCostRateService(rateRepository, workCenterLookupService,
                new WorkOrderIdempotency(idempotencyRecordRepository, new IdempotencySupport(new ObjectMapper())),
                userLookupService);
    }

    @Test
    void save_currentVersionOne_writesVersionTwoRoundedToSixDecimals() {
        center(OrganizationStatus.ACTIVE);
        when(rateRepository.findTopByWorkCenterWorkCenterIdOrderByRateVersionDesc(workCenterId))
                .thenReturn(Optional.of(ProductionCostRate.builder().rateVersion(1).build()));
        when(rateRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RateCommandResult result = service.save(plantId, workCenterId, request(1, "6000", "Operating rate"), "KEY");

        ArgumentCaptor<ProductionCostRate> saved = ArgumentCaptor.forClass(ProductionCostRate.class);
        verify(rateRepository).saveAndFlush(saved.capture());
        assertThat(result.rateVersion()).isEqualTo(2);
        assertThat(saved.getValue().getRateVersion()).isEqualTo(2);
        assertThat(saved.getValue().getLaborRate()).isEqualByComparingTo("6000");
        assertThat(saved.getValue().getLaborRate().scale()).isEqualTo(6);
        assertThat(saved.getValue().getReason()).isEqualTo("Operating rate");
    }

    /** FE A05: rate 0 is valid — it is a known 0, not a missing rate. */
    @Test
    void save_zeroRate_isAccepted() {
        center(OrganizationStatus.ACTIVE);
        when(rateRepository.findTopByWorkCenterWorkCenterIdOrderByRateVersionDesc(workCenterId)).thenReturn(Optional.empty());
        when(rateRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.save(plantId, workCenterId, request(0, "0", "No labor on this cell"), "KEY").rateVersion())
                .isEqualTo(1);
    }

    @Test
    void save_staleVersion_isConcurrentModification_andWritesNothing() {
        center(OrganizationStatus.ACTIVE);
        when(rateRepository.findTopByWorkCenterWorkCenterIdOrderByRateVersionDesc(workCenterId))
                .thenReturn(Optional.of(ProductionCostRate.builder().rateVersion(3).build()));

        assertError(() -> service.save(plantId, workCenterId, request(2, "6000", "Operating rate"), "KEY"),
                BusinessErrorCode.CONCURRENT_MODIFICATION);
        verify(rateRepository, never()).saveAndFlush(any());
        verify(idempotencyRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void save_reasonTooShort_isValidationError_beforeAnyLookup() {
        assertError(() -> service.save(plantId, workCenterId, request(0, "6000", "abc"), "KEY"),
                ValidationErrorCode.INVALID_INPUT);
        verifyNoInteractions(rateRepository, workCenterLookupService);
    }

    @Test
    void save_inactiveWorkCenter_isResourceInactive() {
        center(OrganizationStatus.INACTIVE);

        assertError(() -> service.save(plantId, workCenterId, request(0, "6000", "Operating rate"), "KEY"),
                BusinessErrorCode.RESOURCE_INACTIVE);
        verify(rateRepository, never()).saveAndFlush(any());
    }

    /** A work center of another plant is not found — its existence does not leak across plants (FE R29). */
    @Test
    void save_workCenterOfAnotherPlant_isNotFound() {
        WorkCenter other = WorkCenter.builder().workCenterId(workCenterId).code("WC").status(OrganizationStatus.ACTIVE)
                .plant(Plant.builder().plantId(UUID.randomUUID()).build()).build();
        when(workCenterLookupService.findWorkCenter(workCenterId)).thenReturn(Optional.of(other));

        assertError(() -> service.save(plantId, workCenterId, request(0, "6000", "Operating rate"), "KEY"),
                ValidationErrorCode.RESOURCE_NOT_FOUND);
    }

    private void center(OrganizationStatus status) {
        WorkCenter center = WorkCenter.builder().workCenterId(workCenterId).code("WC").status(status)
                .plant(Plant.builder().plantId(plantId).build()).build();
        when(workCenterLookupService.findWorkCenter(workCenterId)).thenReturn(Optional.of(center));
    }

    private static RateRequest request(int expectedVersion, String labor, String reason) {
        return new RateRequest(expectedVersion, "VND", new BigDecimal(labor), new BigDecimal("3000"), reason);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
