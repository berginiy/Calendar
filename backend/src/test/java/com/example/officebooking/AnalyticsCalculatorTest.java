package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.officebooking.analytics.AnalyticsCalculator;
import com.example.officebooking.analytics.AnalyticsCalculator.Result;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalyticsCalculatorTest {
    // неделя 23–29 ноября 2026: пн..вс
    private static final LocalDate MON = LocalDate.of(2026, 11, 23);
    private static final EnumSet<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private Result sample() {
        Map<LocalDate, Long> office = Map.of(MON, 5L, MON.plusDays(1), 10L, MON.plusDays(3), 5L);
        Map<LocalDate, Long> remote = Map.of(MON, 2L);
        return AnalyticsCalculator.compute(MON, MON.plusDays(6), 10, WEEKDAYS, office, remote, 3);
    }

    @Test
    void totalsAndUtilization() {
        Result r = sample();
        assertThat(r.basis()).isEqualTo("PLANNED");
        assertThat(r.workingDays()).isEqualTo(5);
        assertThat(r.officeBookings()).isEqualTo(20);
        assertThat(r.remoteDays()).isEqualTo(2);
        assertThat(r.cancelled()).isEqualTo(3);
        assertThat(r.overallUtilization()).isEqualTo(40.0); // 20 / (5 дней * 10 мест)
    }

    @Test
    void weekdayStatsCoverOnlyWorkingDays() {
        Result r = sample();
        assertThat(r.byWeekday()).hasSize(5);
        var tuesday = r.byWeekday().stream().filter(s -> s.day() == DayOfWeek.TUESDAY).findFirst().orElseThrow();
        assertThat(tuesday.maxOccupied()).isEqualTo(10);
        assertThat(tuesday.avgUtilization()).isEqualTo(100.0);
    }

    @Test
    void topAndLowDemand() {
        Result r = sample();
        assertThat(r.topDemand().get(0).date()).isEqualTo(MON.plusDays(1));
        assertThat(r.lowDemand().get(0).office()).isEqualTo(0);
        assertThat(r.topDemand()).hasSize(5);
    }

    @Test
    void weeklyAndMonthlySeries() {
        Result r = sample();
        assertThat(r.weekly()).hasSize(1);
        assertThat(r.weekly().get(0).office()).isEqualTo(20);
        assertThat(r.monthly()).hasSize(1);
        assertThat(r.monthly().get(0).period()).isEqualTo("2026-11");
    }

    @Test
    void emptyPeriodHasZeroUtilizationWithoutDivisionByZero() {
        Result r = AnalyticsCalculator.compute(MON.plusDays(5), MON.plusDays(6), 10, WEEKDAYS, Map.of(), Map.of(), 0);
        assertThat(r.workingDays()).isZero();
        assertThat(r.overallUtilization()).isZero();
        assertThat(r.byWeekday()).isEmpty();
    }

    @Test
    void periodCrossingMonthBoundarySplitsMonths() {
        Result r = AnalyticsCalculator.compute(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 1), 10, WEEKDAYS,
                Map.of(LocalDate.of(2026, 11, 30), 1L, LocalDate.of(2026, 12, 1), 2L), Map.of(), 0);
        assertThat(r.monthly()).hasSize(2);
        assertThat(r.weekly()).hasSize(1);
    }
}
