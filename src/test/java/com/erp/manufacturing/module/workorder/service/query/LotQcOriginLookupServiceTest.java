package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import com.erp.manufacturing.module.workorder.repository.QualityDispositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * C2-2: a lot only needs to go through QC disposition before {@code module/inventory}'s generic
 * status-change endpoint may release it from {@code HOLD} if BOTH a production receipt line produced
 * it AND it has never been QC-dispositioned. Checking origin alone would permanently block a lot that
 * was already properly dispositioned once and later manually re-quarantined — these four cases prove
 * both booleans are actually consulted, not just one.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LotQcOriginLookupService tests")
class LotQcOriginLookupServiceTest {

    @Mock ProductionReceiptLineRepository productionReceiptLineRepository;
    @Mock QualityDispositionRepository qualityDispositionRepository;

    LotQcOriginLookupService service;

    private final UUID lotId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LotQcOriginLookupService(productionReceiptLineRepository, qualityDispositionRepository);
    }

    @Test
    @DisplayName("true: produced by a receipt and never dispositioned")
    void requiresQc_producedAndNeverDispositioned_isTrue() {
        when(productionReceiptLineRepository.existsByLotLotId(lotId)).thenReturn(true);
        when(qualityDispositionRepository.existsByLotLotId(lotId)).thenReturn(false);

        assertThat(service.requiresQcDispositionBeforeRelease(lotId)).isTrue();
    }

    @Test
    @DisplayName("false: produced by a receipt but already dispositioned once")
    void requiresQc_producedButAlreadyDispositioned_isFalse() {
        when(productionReceiptLineRepository.existsByLotLotId(lotId)).thenReturn(true);
        when(qualityDispositionRepository.existsByLotLotId(lotId)).thenReturn(true);

        assertThat(service.requiresQcDispositionBeforeRelease(lotId)).isFalse();
    }

    @Test
    @DisplayName("false: never produced by any receipt — short-circuits before consulting disposition history")
    void requiresQc_neverProduced_isFalseWithoutConsultingDispositionHistory() {
        when(productionReceiptLineRepository.existsByLotLotId(lotId)).thenReturn(false);

        assertThat(service.requiresQcDispositionBeforeRelease(lotId)).isFalse();

        verifyNoInteractions(qualityDispositionRepository);
    }
}
