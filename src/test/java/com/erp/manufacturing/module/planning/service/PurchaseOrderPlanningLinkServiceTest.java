package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.planning.domain.SupplyConversionTargetType;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionConversion;
import com.erp.manufacturing.module.planning.dto.PurchaseOrderPlanningSource;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionConversionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseOrderPlanningLinkService (G7a)")
class PurchaseOrderPlanningLinkServiceTest {

    @Mock SupplySuggestionConversionRepository conversionRepository;
    @Mock MrpRunRepository mrpRunRepository;
    @InjectMocks PurchaseOrderPlanningLinkService service;

    private static SupplySuggestionConversion row(UUID runId, UUID orderId, UUID lineId, String quantity) {
        return SupplySuggestionConversion.builder()
                .conversionId(UUID.randomUUID())
                .mrpRunId(runId)
                .supplySuggestionId(UUID.randomUUID())
                .requirementLineId(UUID.randomUUID())
                .entryNo(1)
                .quantity(new BigDecimal(quantity))
                .targetType(SupplyConversionTargetType.PURCHASE_ORDER)
                .targetId(orderId)
                .targetLineId(lineId)
                .targetCode("PO-MRP-1")
                .conversionVersion(1)
                .build();
    }

    @Test
    @DisplayName("release: held rows are marked released (kept, not deleted); each run's conversion version moves once")
    void release_marksRowsAndAdvancesEachRunOnce() {
        UUID orderId = UUID.randomUUID();
        UUID runA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID runB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        SupplySuggestionConversion a1 = row(runA, orderId, UUID.randomUUID(), "10");
        SupplySuggestionConversion a2 = row(runA, orderId, UUID.randomUUID(), "5");
        SupplySuggestionConversion b1 = row(runB, orderId, UUID.randomUUID(), "3");
        when(conversionRepository.findByTargetTypeAndTargetIdAndReleasedAtIsNull(SupplyConversionTargetType.PURCHASE_ORDER, orderId))
                .thenReturn(List.of(b1, a1, a2));
        MrpRun runEntityA = MrpRun.builder().mrpRunId(runA).conversionVersion(4).build();
        MrpRun runEntityB = MrpRun.builder().mrpRunId(runB).conversionVersion(1).build();
        when(mrpRunRepository.lockByMrpRunId(runA)).thenReturn(Optional.of(runEntityA));
        when(mrpRunRepository.lockByMrpRunId(runB)).thenReturn(Optional.of(runEntityB));
        UUID actor = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-18T03:00:00Z");

        int released = service.releasePurchaseOrder(orderId, "Supplier cannot deliver", at, actor);

        assertThat(released).isEqualTo(3);
        assertThat(List.of(a1, a2, b1)).allSatisfy(r -> {
            assertThat(r.getReleasedAt()).isEqualTo(at);
            assertThat(r.getReleasedBy()).isEqualTo(actor);
            assertThat(r.getReleaseReason()).isEqualTo("Supplier cannot deliver");
        });
        assertThat(runEntityA.getConversionVersion()).isEqualTo(5);
        assertThat(runEntityB.getConversionVersion()).isEqualTo(2);
        // Locks in a fixed (sorted) order so two concurrent cancels cannot deadlock.
        InOrder order = inOrder(mrpRunRepository);
        order.verify(mrpRunRepository).lockByMrpRunId(runA);
        order.verify(mrpRunRepository).lockByMrpRunId(runB);
        verify(conversionRepository).saveAll(List.of(b1, a1, a2));
        verify(conversionRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("release: nothing held (already released, or never sourced) changes nothing")
    void release_nothingHeld_isANoOp() {
        UUID orderId = UUID.randomUUID();
        when(conversionRepository.findByTargetTypeAndTargetIdAndReleasedAtIsNull(SupplyConversionTargetType.PURCHASE_ORDER, orderId))
                .thenReturn(List.of());

        assertThat(service.releasePurchaseOrder(orderId, "x", Instant.now(), null)).isZero();

        verifyNoInteractions(mrpRunRepository);
        verify(conversionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("findSources: grouped by purchase order line; run code and source version from one batch")
    void findSources_groupsByLineWithRunData() {
        UUID orderId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        SupplySuggestionConversion held = row(runId, orderId, lineId, "10");
        SupplySuggestionConversion released = row(runId, orderId, lineId, "2");
        released.release("cancelled", Instant.parse("2026-10-18T01:00:00Z"), null);
        when(conversionRepository.findByTargetTypeAndTargetLineIdInOrderByCreatedAtAscEntryNoAsc(
                SupplyConversionTargetType.PURCHASE_ORDER, List.of(lineId))).thenReturn(List.of(held, released));
        when(mrpRunRepository.findAllById(anyCollection())).thenReturn(List.of(
                MrpRun.builder().mrpRunId(runId).code("RUN-ABC").sourceVersion("hash").build()));

        Map<UUID, List<PurchaseOrderPlanningSource>> sources = service.findSources(List.of(lineId));

        assertThat(sources.get(lineId)).hasSize(2);
        assertThat(sources.get(lineId).get(0).mrpRunCode()).isEqualTo("RUN-ABC");
        assertThat(sources.get(lineId).get(0).sourceVersion()).isEqualTo("hash");
        assertThat(sources.get(lineId).get(0).releasedAt()).isNull();
        assertThat(sources.get(lineId).get(1).releaseReason()).isEqualTo("cancelled");
        verify(mrpRunRepository, times(1)).findAllById(anyCollection());
    }

    @Test
    @DisplayName("empty input touches no repository")
    void emptyInputs_touchNothing() {
        assertThat(service.findSources(List.of())).isEmpty();
        assertThat(service.findSourceLockedOrderIds(List.of())).isEmpty();
        verifyNoInteractions(conversionRepository, mrpRunRepository);
    }
}
