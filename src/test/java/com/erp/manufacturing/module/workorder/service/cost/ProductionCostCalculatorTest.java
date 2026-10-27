package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.Baseline;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.BaselineMaterial;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.BaselineOperation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostCheck;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostSnapshot;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.MaterialLine;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.PartAllocation;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ReceiptAllocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G11b: the cost formulas against the FE fixtures F1–F4 (FE_HANDOFF_GD_11 §3.7). Every expected number is the FE's own,
 * copied as a literal — never recomputed with the class under test (R6).
 */
class ProductionCostCalculatorTest {

    private static final UUID WO = UUID.randomUUID();
    private static final UUID COMPONENT = UUID.randomUUID();
    private static final UUID OPERATION = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-10-15T01:00:00Z");

    // ---------------------------------------------------------------- F1

    @Test
    void f1_plannedActualVarianceAndUnitCost_matchTheFeFixture() {
        CostSnapshot s = ProductionCostCalculator.calculate(f1(WorkOrderStatus.COMPLETED, 0));

        assertThat(s.planned().material()).isEqualByComparingTo("20000");
        assertThat(s.planned().labor()).isEqualByComparingTo("6000");
        assertThat(s.planned().overhead()).isEqualByComparingTo("3000");
        assertThat(s.planned().total()).isEqualByComparingTo("29000");
        assertThat(s.actual().material()).isEqualByComparingTo("21000");
        assertThat(s.actual().labor()).isEqualByComparingTo("9000");
        assertThat(s.actual().overhead()).isEqualByComparingTo("4500");
        assertThat(s.actual().total()).isEqualByComparingTo("34500");
        assertThat(s.variance().total()).isEqualByComparingTo("5500");
        assertThat(s.variancePercent()).isEqualByComparingTo("18.965517");
        assertThat(s.unitCost()).isEqualByComparingTo("3833.333333");
        assertThat(s.loss()).isNull();
        assertThat(s.actualMinutes()).isEqualByComparingTo("90");
    }

    /** Material is what was consumed (21 KG), not what was issued (22 KG) — FE A08, Q13. */
    @Test
    void f1_material_isConsumedNotIssued_andTheReturnTakesItsShare() {
        CostSnapshot s = ProductionCostCalculator.calculate(f1(WorkOrderStatus.COMPLETED, 0));

        MaterialLine supplemental = s.materials().get(1);
        assertThat(supplemental.issuedValue()).isEqualByComparingTo("2000");
        assertThat(supplemental.returnedValue()).isEqualByComparingTo("1000");
        assertThat(supplemental.consumedValue()).isEqualByComparingTo("1000");
        assertThat(supplemental.unusedValue()).isEqualByComparingTo("0");
    }

    /** Cumulative allocation from the authoritative total: receipt → QC part → delivery (FE §3.5 #8, F1 table). */
    @Test
    void f1_allocation_isCumulativeAndAddsBackToTheTotal() {
        CostSnapshot s = ProductionCostCalculator.calculate(f1(WorkOrderStatus.COMPLETED, 0));

        ReceiptAllocation pr1 = s.allocations().get(0);
        ReceiptAllocation pr2 = s.allocations().get(1);
        assertThat(pr1.amount()).isEqualByComparingTo("19166.666667");
        assertThat(pr2.amount()).isEqualByComparingTo("15333.333333");
        assertThat(pr1.parts()).extracting(PartAllocation::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("11500"), new BigDecimal("3833.333334"), new BigDecimal("3833.333333"));
        assertThat(pr2.parts().get(0).amount()).isEqualByComparingTo("15333.333333");
        assertThat(s.issuedValue()).isEqualByComparingTo("30666.666667");
        assertThat(s.remainingValue()).isEqualByComparingTo("3833.333333");
        assertThat(s.issuedValue().add(s.remainingValue())).isEqualByComparingTo(s.actual().total());
        assertThat(pr1.parts().get(2).quality()).isEqualTo("REJECTED");
        assertThat(pr1.parts().get(2).remainingQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void f1_everyOperationalAndCostCheckPasses() {
        CostSnapshot s = ProductionCostCalculator.calculate(f1(WorkOrderStatus.COMPLETED, 0));

        assertThat(s.checks()).extracting(CostCheck::code).containsExactly("STATE", "OPERATIONS", "ISSUES",
                "MATERIAL", "RESERVATIONS", "RECEIPTS", "QC", "BASELINE", "COST", "VALUATION");
        assertThat(s.checks()).allMatch(CostCheck::ok);
        assertThat(s.unknownReasons()).isEmpty();
    }

    /** D3: a movement of the output pool that is not this order's own blocks valuation, nothing else. */
    @Test
    void foreignPoolMovement_failsValuationOnly() {
        CostSnapshot s = ProductionCostCalculator.calculate(f1(WorkOrderStatus.COMPLETED, 1));

        Map<String, CostCheck> checks = byCode(s);
        assertThat(checks.get("VALUATION").ok()).isFalse();
        assertThat(checks.get("VALUATION").reasonCodes()).containsExactly("POOL_MIXED");
        assertThat(checks.get("COST").ok()).isTrue();
        assertThat(s.unknownReasons()).contains("UNRECONCILED_VALUE");
    }

    @Test
    void notCompleted_failsStateWithTheStatus() {
        CostSnapshot s = ProductionCostCalculator.calculate(f1(WorkOrderStatus.IN_PROGRESS, 0));

        assertThat(byCode(s).get("STATE").reasonCodes()).containsExactly("STATUS_IN_PROGRESS");
    }

    // ---------------------------------------------------------------- F2 / F3 / F4

    @Test
    void f2_actualEqualsPlanned_varianceZero_unitCost2900() {
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline(), new BigDecimal("10"),
                List.of(issue(new BigDecimal("20"), new BigDecimal("20"), List.of())),
                List.of(execution(T0, T0.plusSeconds(3600), null)),
                List.of(receipt(new BigDecimal("10"), List.of(decision(new BigDecimal("10"), BigDecimal.ZERO)))),
                List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.actual().total()).isEqualByComparingTo("29000");
        assertThat(s.variance().total()).isEqualByComparingTo("0");
        assertThat(s.variancePercent()).isEqualByComparingTo("0");
        assertThat(s.unitCost()).isEqualByComparingTo("2900");
    }

    /** F3: all scrap — no unit cost, the whole known total is loss (FE R15, Q24). */
    @Test
    void f3_goodZero_unitCostNull_lossIsTheActualTotal() {
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline(), BigDecimal.ZERO,
                List.of(issue(new BigDecimal("21"), new BigDecimal("21"), List.of())),
                List.of(execution(T0, T0.plusSeconds(5400), null)), List.of(), List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.unitCost()).isNull();
        assertThat(s.loss()).isEqualByComparingTo("34500");
        assertThat(s.allocations()).isEmpty();
        assertThat(byCode(s).get("VALUATION").ok()).isTrue();
    }

    /** F4 / GĐ8 Q37: the return that empties the line takes the residual: .333333 then .333334. */
    @Test
    void f4_fractionalReturns_lastReturnTakesTheResidual() {
        BaselineMaterial material = new BaselineMaterial(COMPONENT, 1, UUID.randomUUID(), null, "KG", new BigDecimal("3"),
                new BigDecimal("0.333333333333"), new BigDecimal("1.000000"));
        Baseline baseline = new Baseline(UUID.randomUUID(), T0, null, UUID.randomUUID(), BigDecimal.ZERO,
                BigDecimal.ONE, "VND", null, List.of(material), List.of(operation(new BigDecimal("6000"))));
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline, BigDecimal.ONE,
                List.of(issue(new BigDecimal("3"), BigDecimal.ONE, List.of(BigDecimal.ONE, BigDecimal.ONE))),
                List.of(), List.of(), List.of(), 0);

        MaterialLine line = ProductionCostCalculator.calculate(in).materials().get(0);

        assertThat(line.issuedValue()).isEqualByComparingTo("1");
        assertThat(line.returns().get(0).value()).isEqualByComparingTo("0.333333");
        assertThat(line.returns().get(1).value()).isEqualByComparingTo("0.333334");
        assertThat(line.consumedValue()).isEqualByComparingTo("0.333333");
    }

    // ---------------------------------------------------------------- unknown is not zero

    @Test
    void noBaseline_plannedUnknown_neverZero() {
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, null, new BigDecimal("10"),
                List.of(issue(new BigDecimal("20"), new BigDecimal("20"), List.of())),
                List.of(execution(T0, T0.plusSeconds(3600), null)), List.of(), List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.planned().total()).isNull();
        assertThat(s.actual().material()).isNull();
        assertThat(s.unknownReasons()).contains("MISSING_BASELINE");
        assertThat(byCode(s).get("BASELINE").reasonCodes()).containsExactly("MISSING_BASELINE");
    }

    /** FE A05: a rate of 0 saved on purpose is a known 0, not a missing rate. */
    @Test
    void rateZero_isKnownZero() {
        Baseline baseline = new Baseline(UUID.randomUUID(), T0, null, UUID.randomUUID(), BigDecimal.ZERO,
                BigDecimal.TEN, "VND", null, List.of(baselineMaterial()), List.of(operation(BigDecimal.ZERO)));
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline, new BigDecimal("10"),
                List.of(issue(new BigDecimal("20"), new BigDecimal("20"), List.of())),
                List.of(execution(T0, T0.plusSeconds(3600), null)), List.of(), List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.actual().labor()).isEqualByComparingTo("0");
        assertThat(s.unknownReasons()).doesNotContain("MISSING_RATE");
    }

    /** FE A07 / Q14: a required component with no issue at all is unknown, not 0. */
    @Test
    void requiredComponentWithoutIssue_actualMaterialUnknown() {
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline(), new BigDecimal("10"), List.of(),
                List.of(execution(T0, T0.plusSeconds(3600), null)), List.of(), List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.actual().material()).isNull();
        assertThat(s.actual().total()).isNull();
        assertThat(s.unknownReasons()).contains("MISSING_CONSUMPTION");
    }

    /** D11 / FE A13: two executions of one operation that overlap make its time unknown — never double-charged. */
    @Test
    void overlappingExecutions_timeUnknown() {
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline(), new BigDecimal("10"),
                List.of(issue(new BigDecimal("20"), new BigDecimal("20"), List.of())),
                List.of(execution(T0, T0.plusSeconds(3600), null), execution(T0.plusSeconds(1800), T0.plusSeconds(5400), null)),
                List.of(), List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.actual().labor()).isNull();
        assertThat(s.unknownReasons()).contains("INVALID_TIME");
        assertThat(byCode(s).get("COST").reasonCodes()).contains("INVALID_TIME");
    }

    /** FE A25: unused material blocks the close (MATERIAL) and its preview value is the consumed share. */
    @Test
    void unusedMaterial_failsMaterialAndValuesTheConsumedShare() {
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline(), new BigDecimal("10"),
                List.of(issue(new BigDecimal("20"), new BigDecimal("15"), List.of())),
                List.of(execution(T0, T0.plusSeconds(3600), null)), List.of(), List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.materials().get(0).consumedValue()).isEqualByComparingTo("15000");
        assertThat(s.materials().get(0).unusedValue()).isEqualByComparingTo("5000");
        assertThat(byCode(s).get("MATERIAL").reasonCodes()).containsExactly("UNUSED_MATERIAL");
    }

    @Test
    void varianceBelowZero_keepsItsSign() {
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline(), new BigDecimal("10"),
                List.of(issue(new BigDecimal("20"), new BigDecimal("20"), List.of())),
                List.of(execution(T0, T0.plusSeconds(3599), null)), List.of(), List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.variance().total()).isEqualByComparingTo("-2.5");
        assertThat(s.variancePercent()).isEqualByComparingTo("-0.008621");
    }

    /**
     * Three receipts of 1 out of a total of 10: shares taken one by one would be 3.333333 × 3 = 9.999999. Cumulative
     * shares give 3.333333, 3.333334, 3.333333 — the parts add back to the total (FE Q19). F1's numbers cannot tell the
     * two apart (they happen to add up), which is why this case exists.
     */
    @Test
    void cumulativeReceiptShares_addBackToTheTotalWhereSeparateSharesWouldNot() {
        BaselineMaterial material = new BaselineMaterial(COMPONENT, 1, UUID.randomUUID(), null, "KG", new BigDecimal("20"),
                BigDecimal.ONE, new BigDecimal("20"));
        Baseline baseline = new Baseline(UUID.randomUUID(), T0, null, UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.TEN,
                "VND", null, List.of(material), List.of(operation(new BigDecimal("6000"))));
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline, new BigDecimal("3"),
                List.of(issue(BigDecimal.TEN, BigDecimal.TEN, List.of())), List.of(),
                List.of(receipt(BigDecimal.ONE, List.of()), receipt(BigDecimal.ONE, List.of()), receipt(BigDecimal.ONE, List.of())),
                List.of(), 0);

        CostSnapshot s = ProductionCostCalculator.calculate(in);

        assertThat(s.actual().total()).isEqualByComparingTo("10");
        assertThat(s.allocations()).extracting(ReceiptAllocation::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3.333333"), new BigDecimal("3.333334"), new BigDecimal("3.333333"));
    }

    /**
     * When both the consumed share and the returned share round up (a 7th decimal of exactly 5), the return that empties
     * the line takes the residual so issued = consumed + returned in value (FE §3.5 #5): issued 0.000001 for 2 KG,
     * consumed 1 ⇒ consumed value 0.000001, the return 0 — not 0.000001 each.
     */
    @Test
    void lastReturn_takesTheResidual_whenBothSharesRoundUp() {
        BaselineMaterial material = new BaselineMaterial(COMPONENT, 1, UUID.randomUUID(), null, "KG", new BigDecimal("2"),
                new BigDecimal("0.0000005"), new BigDecimal("0.000001"));
        Baseline baseline = new Baseline(UUID.randomUUID(), T0, null, UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ONE,
                "VND", null, List.of(material), List.of(operation(new BigDecimal("6000"))));
        CostInputs in = inputs(WorkOrderStatus.COMPLETED, baseline, BigDecimal.ONE,
                List.of(issue(new BigDecimal("2"), BigDecimal.ONE, List.of(BigDecimal.ONE))), List.of(), List.of(), List.of(), 0);

        MaterialLine line = ProductionCostCalculator.calculate(in).materials().get(0);

        assertThat(line.issuedValue()).isEqualByComparingTo("0.000001");
        assertThat(line.returns().get(0).value()).isEqualByComparingTo("0");
        assertThat(line.consumedValue()).isEqualByComparingTo("0.000001");
    }

    /** FE Q9: planned active minutes are divided by the capacity units captured at release. */
    @Test
    void plannedMinutes_divideActiveTimeByCapacityUnits() {
        var operation = com.erp.manufacturing.module.workorder.domain.WorkOrderOperation.builder()
                .setupMinutes(new BigDecimal("5")).runMinutesPerUnit(BigDecimal.ONE).build();

        assertThat(ProductionCostBaselineService.plannedMinutes(operation, BigDecimal.TEN, 2)).isEqualByComparingTo("7.5");
        assertThat(ProductionCostBaselineService.plannedMinutes(operation, BigDecimal.TEN, null)).isNull();
    }

    // ---------------------------------------------------------------- fixtures

    /** F1: issue 20 + supplemental 2 (1 returned); 60 + 30 retry minutes; receipts 5 + 4; QC 3/1/1 and 4; delivered 8. */
    private static CostInputs f1(WorkOrderStatus status, int foreign) {
        UUID lotA = UUID.randomUUID();
        UUID lotB = UUID.randomUUID();
        UUID lotR = UUID.randomUUID();
        UUID lotC = UUID.randomUUID();
        UUID ex1 = UUID.randomUUID();
        CostInputs.Receipt pr1 = new CostInputs.Receipt(UUID.randomUUID(), "PR1", UUID.randomUUID(), T0.plusSeconds(7200),
                new BigDecimal("5"), UUID.randomUUID(), List.of(
                new CostInputs.Decision(UUID.randomUUID(), T0.plusSeconds(8000), new BigDecimal("3"), BigDecimal.ZERO, lotA, null),
                new CostInputs.Decision(UUID.randomUUID(), T0.plusSeconds(8100), BigDecimal.ONE, BigDecimal.ONE, lotB, lotR)));
        CostInputs.Receipt pr2 = new CostInputs.Receipt(UUID.randomUUID(), "PR2", UUID.randomUUID(), T0.plusSeconds(7300),
                new BigDecimal("4"), UUID.randomUUID(), List.of(
                new CostInputs.Decision(UUID.randomUUID(), T0.plusSeconds(8200), new BigDecimal("4"), BigDecimal.ZERO, lotC, null)));
        UUID dn1 = UUID.randomUUID();
        UUID dn2 = UUID.randomUUID();
        List<CostInputs.DeliveryLine> deliveries = List.of(
                new CostInputs.DeliveryLine(dn1, "DN1", UUID.randomUUID(), lotA, new BigDecimal("3"), UUID.randomUUID()),
                new CostInputs.DeliveryLine(dn2, "DN2", UUID.randomUUID(), lotB, BigDecimal.ONE, UUID.randomUUID()),
                new CostInputs.DeliveryLine(dn2, "DN2", UUID.randomUUID(), lotC, new BigDecimal("4"), UUID.randomUUID()));
        return new CostInputs(WO, status, true, new BigDecimal("10"), new BigDecimal("9"), BigDecimal.ONE, BigDecimal.ONE,
                baseline(), List.of(new CostInputs.Requirement(COMPONENT, new BigDecimal("20"))),
                List.of(issue(new BigDecimal("20"), new BigDecimal("20"), List.of()),
                        issue(new BigDecimal("2"), BigDecimal.ONE, List.of(BigDecimal.ONE))),
                List.of(new CostInputs.Operation(OPERATION, 10, "CNC", true, true, new BigDecimal("10"),
                        new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("9"), new BigDecimal("9"))),
                List.of(new CostInputs.Execution(ex1, "PE-1", OPERATION, null, T0, T0.plusSeconds(3600)),
                        new CostInputs.Execution(UUID.randomUUID(), "PE-2", OPERATION, ex1, T0.plusSeconds(3600),
                                T0.plusSeconds(5400))),
                List.of(pr1, pr2), false, false, BigDecimal.ZERO, deliveries, foreign);
    }

    private static CostInputs inputs(WorkOrderStatus status, Baseline baseline, BigDecimal good,
                                     List<CostInputs.IssueLine> issues, List<CostInputs.Execution> executions,
                                     List<CostInputs.Receipt> receipts, List<CostInputs.DeliveryLine> deliveries,
                                     int foreign) {
        return new CostInputs(WO, status, true, new BigDecimal("10"), good, BigDecimal.ZERO, BigDecimal.ZERO, baseline,
                List.of(new CostInputs.Requirement(COMPONENT, new BigDecimal("20"))), issues,
                List.of(new CostInputs.Operation(OPERATION, 10, "CNC", true, true, BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.ZERO, good, good)),
                executions, receipts, false, false, BigDecimal.ZERO, deliveries, foreign);
    }

    private static Baseline baseline() {
        return new Baseline(UUID.randomUUID(), T0, null, UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.TEN, "VND", null,
                List.of(baselineMaterial()), List.of(operation(new BigDecimal("6000"))));
    }

    private static BaselineMaterial baselineMaterial() {
        return new BaselineMaterial(COMPONENT, 1, UUID.randomUUID(), null, "KG", new BigDecimal("20"),
                new BigDecimal("1000"), new BigDecimal("20000"));
    }

    /** Capacity 1, 60 planned minutes, labor rate as given and overhead half of it. */
    private static BaselineOperation operation(BigDecimal laborRate) {
        BigDecimal overheadRate = laborRate.divide(BigDecimal.valueOf(2));
        return new BaselineOperation(OPERATION, 10, "CNC", UUID.randomUUID(), 1, new BigDecimal("60"), 1, laborRate,
                overheadRate, CostMath.minutesCost(new BigDecimal("60"), laborRate),
                CostMath.minutesCost(new BigDecimal("60"), overheadRate));
    }

    private static CostInputs.IssueLine issue(BigDecimal quantity, BigDecimal consumed, List<BigDecimal> returns) {
        List<CostInputs.Return> rows = returns.stream().map(q -> new CostInputs.Return(UUID.randomUUID(), "MR",
                UUID.randomUUID(), UUID.randomUUID(), q, T0)).toList();
        return new CostInputs.IssueLine(UUID.randomUUID(), "MI", UUID.randomUUID(), UUID.randomUUID(), COMPONENT,
                UUID.randomUUID(), "RM", UUID.randomUUID(), null, "KG", quantity, consumed, rows);
    }

    private static CostInputs.Execution execution(Instant start, Instant end, UUID source) {
        return new CostInputs.Execution(UUID.randomUUID(), "PE", OPERATION, source, start, end);
    }

    private static CostInputs.Receipt receipt(BigDecimal quantity, List<CostInputs.Decision> decisions) {
        return new CostInputs.Receipt(UUID.randomUUID(), "PR", UUID.randomUUID(), T0, quantity, null, decisions);
    }

    private static CostInputs.Decision decision(BigDecimal accepted, BigDecimal rejected) {
        return new CostInputs.Decision(UUID.randomUUID(), T0, accepted, rejected, null, null);
    }

    private static Map<String, CostCheck> byCode(CostSnapshot s) {
        return s.checks().stream().collect(Collectors.toMap(CostCheck::code, Function.identity()));
    }
}
