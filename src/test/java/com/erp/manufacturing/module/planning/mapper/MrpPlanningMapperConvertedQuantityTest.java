package com.erp.manufacturing.module.planning.mapper;

import com.erp.manufacturing.module.planning.domain.SupplySuggestion;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G7a: the converted quantity of a proposal once a purchase order it created was cancelled. The
 * proposal stays {@code CONVERTED}; only its ledger rows say the quantity went back.
 */
@DisplayName("MrpPlanningMapper.convertedQuantity (G7a, released ledger rows)")
class MrpPlanningMapperConvertedQuantityTest {

    private static SupplySuggestion converted() {
        return SupplySuggestion.builder()
                .suggestedQuantity(new BigDecimal("40"))
                .status(SupplySuggestionStatus.CONVERTED)
                .build();
    }

    @Test
    @DisplayName("legacy one-shot convert (no ledger row at all) counts as fully converted")
    void noLedgerRows_convertedProposal_isFullyConverted() {
        assertThat(MrpPlanningMapper.convertedQuantity(converted(), BigDecimal.ZERO, false)).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("ledger rows exist but every one was released ⇒ nothing is converted any more, the quota is back")
    void allRowsReleased_isZero() {
        SupplySuggestion suggestion = converted();

        assertThat(MrpPlanningMapper.convertedQuantity(suggestion, BigDecimal.ZERO, true)).isEqualByComparingTo("0");
        assertThat(MrpPlanningMapper.remaining(suggestion.getSuggestedQuantity(),
                MrpPlanningMapper.convertedQuantity(suggestion, BigDecimal.ZERO, true))).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("some rows held ⇒ only their quantity counts")
    void partlyReleased_countsTheHeldRowsOnly() {
        assertThat(MrpPlanningMapper.convertedQuantity(converted(), new BigDecimal("15"), true)).isEqualByComparingTo("15");
    }
}
