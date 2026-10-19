package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.planning.domain.SupplyConversionTargetType;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionConversion;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionConversionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** G8a (FE R06/Q21): cancelling a work order gives its planning quota back — once, keeping the trace. */
@ExtendWith(MockitoExtension.class)
class WorkOrderPlanningLinkServiceTest {

    @Mock SupplySuggestionConversionRepository conversionRepository;
    @Mock MrpRunRepository mrpRunRepository;
    @InjectMocks WorkOrderPlanningLinkService service;

    @Test
    @DisplayName("release: the work order's held rows are marked released and the run's conversion version moves on")
    void release_marksTheWorkOrderRowsReleased_andAdvancesTheRun() {
        UUID workOrderId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        SupplySuggestionConversion held = SupplySuggestionConversion.builder()
                .conversionId(UUID.randomUUID()).mrpRunId(runId).supplySuggestionId(UUID.randomUUID())
                .requirementLineId(UUID.randomUUID()).conversionBatchId(UUID.randomUUID()).entryNo(1)
                .quantity(new BigDecimal("40")).targetType(SupplyConversionTargetType.WORK_ORDER)
                .targetId(workOrderId).targetCode("WO-1").conversionVersion(1).build();
        when(conversionRepository.findByTargetTypeAndTargetIdAndReleasedAtIsNull(
                SupplyConversionTargetType.WORK_ORDER, workOrderId)).thenReturn(List.of(held));
        MrpRun run = MrpRun.builder().mrpRunId(runId).conversionVersion(2).build();
        when(mrpRunRepository.lockByMrpRunId(runId)).thenReturn(Optional.of(run));
        Instant at = Instant.parse("2026-10-19T03:00:00Z");

        int released = service.releaseWorkOrder(workOrderId, "Customer withdrew", at, null);

        assertThat(released).isEqualTo(1);
        assertThat(held.getReleasedAt()).isEqualTo(at);
        assertThat(held.getReleaseReason()).isEqualTo("Customer withdrew");
        assertThat(run.getConversionVersion()).isEqualTo(3);
        verify(conversionRepository, never()).deleteAll(any());
    }

    /** A purchase order's rows are a different target: the work order release never reads them. */
    @Test
    void release_asksForWorkOrderRowsOnly_andNothingHeldChangesNothing() {
        UUID workOrderId = UUID.randomUUID();
        when(conversionRepository.findByTargetTypeAndTargetIdAndReleasedAtIsNull(
                SupplyConversionTargetType.WORK_ORDER, workOrderId)).thenReturn(List.of());

        assertThat(service.releaseWorkOrder(workOrderId, "x", Instant.now(), null)).isZero();

        verify(conversionRepository, never()).findByTargetTypeAndTargetIdAndReleasedAtIsNull(
                eq(SupplyConversionTargetType.PURCHASE_ORDER), any());
        verifyNoInteractions(mrpRunRepository);
    }
}
