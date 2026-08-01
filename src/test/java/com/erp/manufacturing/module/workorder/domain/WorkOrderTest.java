package com.erp.manufacturing.module.workorder.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two pieces of behaviour F8 put on the entity rather than in a service, because spec §5.2/§5.3
 * describe them as state of the work order itself.
 */
@DisplayName("WorkOrder – F8 execution timeline and completion percentage")
class WorkOrderTest {

    /**
     * Scale and rounding are part of the contract, not an implementation detail: the frontend renders
     * this straight into a progress badge (spec §5.2). Asserting {@code isNotNull()} here would be
     * exactly the empty assertion rule R6 exists to forbid — 2/3 is the case that can only pass with
     * the right scale <em>and</em> the right rounding mode.
     */
    @Test
    void completionPercent_roundsToTwoDecimalsHalfUp() {
        WorkOrder workOrder = workOrder(new BigDecimal("3.000000"));
        workOrder.setActualGoodQuantity(new BigDecimal("2.000000"));

        // 2/3 = 66.666... ⇒ 66.67 under HALF_UP at scale 2 (66.66 would mean HALF_DOWN/FLOOR).
        assertThat(workOrder.completionPercent()).isEqualByComparingTo("66.67");
        assertThat(workOrder.completionPercent().scale()).isEqualTo(2);
    }

    @Test
    void completionPercent_atThePlan_isExactlyOneHundred() {
        WorkOrder workOrder = workOrder(new BigDecimal("10.000000"));
        workOrder.setActualGoodQuantity(new BigDecimal("10.000000"));

        assertThat(workOrder.completionPercent()).isEqualByComparingTo("100.00");
    }

    /** Unreachable through the API ({@code requirePositive}), but dividing by it would throw. */
    @Test
    void completionPercent_withoutAPlannedQuantity_isZeroRatherThanADivideByZero() {
        WorkOrder workOrder = workOrder(BigDecimal.ZERO);
        workOrder.setActualGoodQuantity(new BigDecimal("5.000000"));

        assertThat(workOrder.completionPercent()).isEqualByComparingTo("0");
    }

    /**
     * Spec §5.3: "Lần post đầu tiên đặt executionStartedAt nếu chưa có." It marks when production
     * began, so a later report must leave it where it is.
     */
    @Test
    void reportProduction_setsExecutionStartedAtOnceAndNeverMovesIt() {
        WorkOrder workOrder = workOrder(new BigDecimal("10.000000"));
        workOrder.setStatus(WorkOrderStatus.RELEASED);
        Instant first = Instant.parse("2026-07-31T08:00:00Z");
        Instant later = Instant.parse("2026-07-31T16:00:00Z");

        workOrder.reportProduction(new BigDecimal("3"), BigDecimal.ZERO, BigDecimal.ZERO, first);
        assertThat(workOrder.getExecutionStartedAt()).isEqualTo(first);

        workOrder.reportProduction(new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO, later);
        assertThat(workOrder.getExecutionStartedAt()).isEqualTo(first);
    }

    /**
     * The reason F8 added no {@code execution_completed_at} column: completing the work order and
     * completing execution are the same event, because {@link WorkOrder#complete(Instant)} is only
     * ever reached from here (invariant B53).
     */
    @Test
    void reportProduction_reachingThePlan_closesTheExecutionWindow() {
        WorkOrder workOrder = workOrder(new BigDecimal("10.000000"));
        workOrder.setStatus(WorkOrderStatus.RELEASED);
        Instant started = Instant.parse("2026-07-31T08:00:00Z");
        Instant finished = Instant.parse("2026-07-31T16:00:00Z");

        workOrder.reportProduction(new BigDecimal("4"), BigDecimal.ZERO, BigDecimal.ZERO, started);
        workOrder.reportProduction(new BigDecimal("6"), BigDecimal.ZERO, BigDecimal.ZERO, finished);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(workOrder.getExecutionStartedAt()).isEqualTo(started);
        assertThat(workOrder.getCompletedAt()).isEqualTo(finished);
        assertThat(workOrder.completionPercent()).isEqualByComparingTo("100.00");
    }

    private WorkOrder workOrder(BigDecimal plannedQuantity) {
        return WorkOrder.builder()
                .plannedQuantity(plannedQuantity)
                .completedQuantity(BigDecimal.ZERO)
                .actualGoodQuantity(BigDecimal.ZERO)
                .actualScrapQuantity(BigDecimal.ZERO)
                .actualReworkQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.DRAFT)
                .build();
    }
}
