package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C2-2 / G9c: a lot must go through production QC before {@code module/inventory}'s generic status-change endpoint
 * may release it from {@code HOLD} while the APPROVED receipt line that produced it still has something pending. A
 * line QC has finished with — whole-lot or partial — frees the lot, so one quarantined again by hand stays
 * releasable. The rule itself is JPQL ({@code ProductionReceiptRepositoryIT.existsPendingQualityForLot_*}); these cases
 * pin what the service asks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LotQcOriginLookupService tests")
class LotQcOriginLookupServiceTest {

    @Mock ProductionReceiptLineRepository productionReceiptLineRepository;

    LotQcOriginLookupService service;

    private final UUID lotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LotQcOriginLookupService(productionReceiptLineRepository);
    }

    @Test
    @DisplayName("true: an approved receipt line still has QC pending on the lot")
    void requiresQc_pendingOnAnApprovedLine_isTrue() {
        when(productionReceiptLineRepository.existsPendingQualityForLot(lotId, ProductionReceiptStatus.APPROVED))
                .thenReturn(true);

        assertThat(service.requiresQcDispositionBeforeRelease(lotId)).isTrue();
    }

    @Test
    @DisplayName("false: QC finished with every line of the lot (whole-lot or partial)")
    void requiresQc_nothingPending_isFalse() {
        when(productionReceiptLineRepository.existsPendingQualityForLot(lotId, ProductionReceiptStatus.APPROVED))
                .thenReturn(false);

        assertThat(service.requiresQcDispositionBeforeRelease(lotId)).isFalse();
    }

    @Test
    @DisplayName("asks about APPROVED lines only — a draft never put anything on hold")
    void requiresQc_asksAboutApprovedLinesOnly() {
        service.requiresQcDispositionBeforeRelease(lotId);

        verify(productionReceiptLineRepository).existsPendingQualityForLot(lotId, ProductionReceiptStatus.APPROVED);
    }
}
