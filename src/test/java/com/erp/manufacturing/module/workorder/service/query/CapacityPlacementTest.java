package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.shift.service.TimeInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CF-5 (B269): the one walk the plant capacity preview and release share. Fixtures are the FE request's — a day
 * with 08:00–12:00 and 13:00–17:00, an operation of 10 units × 12 min (120 capacity minutes).
 */
@DisplayName("CapacityPlacement")
class CapacityPlacementTest {

    private static final List<TimeInterval> DAY = List.of(
            new TimeInterval(at("08:00"), at("12:00")),
            new TimeInterval(at("13:00"), at("17:00")));
    private static final Instant HORIZON = Instant.parse("2026-10-14T00:00:00Z");

    @Test
    void twoUnits_halveTheElapsedTime_120CapacityMinutesFrom0800EndAt0900() {
        CapacityPlacement.Result result = CapacityPlacement.place(DAY, at("08:00"), HORIZON, null,
                CapacityPlacement.requiredMinutes(BigDecimal.ZERO, new BigDecimal("12"), BigDecimal.TEN), 2);

        assertThat(result.segments()).hasSize(1);
        assertThat(result.segments().get(0).start()).isEqualTo(at("08:00"));
        assertThat(result.segments().get(0).end()).isEqualTo(at("09:00"));
        assertThat(result.segments().get(0).capacityMinutes()).isEqualByComparingTo("120");
        assertThat(result.remaining()).isEqualByComparingTo("0");
    }

    @Test
    void oneUnit_120CapacityMinutesFrom0800EndAt1000() {
        CapacityPlacement.Result result = CapacityPlacement.place(DAY, at("08:00"), HORIZON, null,
                new BigDecimal("120.000000"), 1);

        assertThat(result.segments()).extracting(CapacityPlacement.Segment::end).containsExactly(at("10:00"));
        assertThat(result.remaining()).isEqualByComparingTo("0");
    }

    /** 11:30–12:00 holds 30 × 2 = 60; the other 60 take 30 minutes after lunch. */
    @Test
    void startAt1130_crossesTheBreak_segments1130To1200And1300To1330() {
        CapacityPlacement.Result result = CapacityPlacement.place(DAY, at("11:30"), HORIZON, null,
                new BigDecimal("120.000000"), 2);

        assertThat(result.segments()).extracting(CapacityPlacement.Segment::start)
                .containsExactly(at("11:30"), at("13:00"));
        assertThat(result.segments()).extracting(CapacityPlacement.Segment::end)
                .containsExactly(at("12:00"), at("13:30"));
        assertThat(result.segments()).extracting(CapacityPlacement.Segment::capacityMinutes)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("60"), new BigDecimal("60"));
    }

    /** 25 capacity minutes on 2 units = 12.5 minutes = 750 s; partial seconds round up, never down. */
    @Test
    void partialMinute_endsOnTheSecond_750SecondsFor25OnTwoUnits() {
        CapacityPlacement.Result result = CapacityPlacement.place(DAY, at("08:00"), HORIZON, null,
                new BigDecimal("25"), 2);

        assertThat(result.segments().get(0).end()).isEqualTo(at("08:00").plusSeconds(750));
    }

    /** 10 capacity minutes on 3 units = 200 s exactly; 1 on 7 units = 8.57 s ⇒ 9 s (ceil). */
    @Test
    void secondsAreCeiled_neverFloored() {
        assertThat(CapacityPlacement.place(DAY, at("08:00"), HORIZON, null, new BigDecimal("1"), 7)
                .segments().get(0).end()).isEqualTo(at("08:00").plusSeconds(9));
        assertThat(CapacityPlacement.continuousSeconds(new BigDecimal("1"), 7)).isEqualTo(9L);
        assertThat(CapacityPlacement.continuousSeconds(new BigDecimal("25"), 2)).isEqualTo(750L);
        assertThat(CapacityPlacement.continuousSeconds(new BigDecimal("120"), 1)).isEqualTo(7200L);
    }

    @Test
    void boundary_splitsAWindowThatCrossesIt() {
        CapacityPlacement.Result result = CapacityPlacement.place(DAY, at("08:00"), HORIZON, at("09:00"),
                new BigDecimal("180"), 1);

        assertThat(result.segments()).extracting(CapacityPlacement.Segment::start)
                .containsExactly(at("08:00"), at("09:00"));
        assertThat(result.segments()).extracting(CapacityPlacement.Segment::end)
                .containsExactly(at("09:00"), at("11:00"));
        assertThat(result.segments()).extracting(CapacityPlacement.Segment::capacityMinutes)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("60"), new BigDecimal("120"));
    }

    @Test
    void noBoundary_keepsTheWindowWhole() {
        CapacityPlacement.Result result = CapacityPlacement.place(DAY, at("08:00"), HORIZON, null,
                new BigDecimal("180"), 1);

        assertThat(result.segments()).hasSize(1);
        assertThat(result.segments().get(0).end()).isEqualTo(at("11:00"));
    }

    /** 8 working hours × 2 units = 960; 1000 leaves 40 to place elsewhere. */
    @Test
    void windowsExhausted_reportTheRemainder() {
        CapacityPlacement.Result result = CapacityPlacement.place(DAY, at("08:00"), HORIZON, null,
                new BigDecimal("1000"), 2);

        assertThat(result.segments()).hasSize(2);
        assertThat(result.segments().get(1).end()).isEqualTo(at("17:00"));
        assertThat(result.remaining()).isEqualByComparingTo("40");
    }

    @Test
    void horizon_stopsPlacementBeforeIt() {
        CapacityPlacement.Result result = CapacityPlacement.place(DAY, at("08:00"), at("10:00"), null,
                new BigDecimal("180"), 1);

        assertThat(result.segments()).extracting(CapacityPlacement.Segment::end).containsExactly(at("10:00"));
        assertThat(result.remaining()).isEqualByComparingTo("60");
    }

    @Test
    void arithmetic_requiredAndActiveElapsed_scaleSixHalfUp() {
        assertThat(CapacityPlacement.requiredMinutes(new BigDecimal("30"), new BigDecimal("2"), new BigDecimal("500")))
                .isEqualByComparingTo("1030").satisfies(value -> assertThat(value.scale()).isEqualTo(6));
        assertThat(CapacityPlacement.activeElapsedMinutes(new BigDecimal("120"), 2)).isEqualByComparingTo("60");
        assertThat(CapacityPlacement.activeElapsedMinutes(new BigDecimal("10"), 3)).isEqualByComparingTo("3.333333");
        assertThat(CapacityPlacement.activeElapsedMinutes(new BigDecimal("20"), 3)).isEqualByComparingTo("6.666667");
    }

    private static Instant at(String time) {
        return Instant.parse("2026-10-13T" + time + ":00Z");
    }
}
