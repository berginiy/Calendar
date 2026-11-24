package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.officebooking.presence.BookingRules;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit-тесты правил по датам. «Сейчас» зафиксировано: понедельник 2026-11-23, 10:00 по Бишкеку. */
class BookingRulesTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Bishkek");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T04:00:00Z"), ZONE);
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private final BookingRules rules = new BookingRules(CLOCK);

    @Test
    void todayIsMondayInBishkek() {
        assertThat(rules.today()).isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    void pastDateIsRejected() {
        assertThat(rules.unavailableReason(LocalDate.of(2026, 11, 20), true, WEEKDAYS)).isPresent();
    }

    @Test
    void todayIsRejectedWhenSameDayBookingDisabled() {
        assertThat(rules.unavailableReason(LocalDate.of(2026, 11, 23), false, WEEKDAYS)).isPresent();
    }

    @Test
    void todayIsAllowedWhenSameDayBookingEnabled() {
        assertThat(rules.unavailableReason(LocalDate.of(2026, 11, 23), true, WEEKDAYS)).isEmpty();
    }

    @Test
    void weekendIsRejectedWhenNotWorkingDay() {
        assertThat(rules.unavailableReason(LocalDate.of(2026, 11, 28), false, WEEKDAYS)).isPresent();
    }

    @Test
    void weekendIsAllowedWhenConfiguredAsWorkingDay() {
        Set<DayOfWeek> all = EnumSet.allOf(DayOfWeek.class);
        assertThat(rules.unavailableReason(LocalDate.of(2026, 11, 28), false, all)).isEmpty();
    }

    @Test
    void futureWorkingDayIsAllowed() {
        assertThat(rules.unavailableReason(LocalDate.of(2026, 11, 24), false, WEEKDAYS)).isEmpty();
    }

    @Test
    void zeroDeadlineAllowsCancellationBeforeStartOfDay() {
        assertThat(rules.cancellationBlockedReason(LocalDate.of(2026, 11, 24), 0)).isEmpty();
    }

    @Test
    void zeroDeadlineBlocksCancellationOnTheDayItself() {
        assertThat(rules.cancellationBlockedReason(LocalDate.of(2026, 11, 23), 0)).isPresent();
    }

    @Test
    void deadlineOf24HoursBlocksCancellationForTomorrow() {
        assertThat(rules.cancellationBlockedReason(LocalDate.of(2026, 11, 24), 24)).isPresent();
        assertThat(rules.cancellationBlockedReason(LocalDate.of(2026, 11, 25), 24)).isEmpty();
    }
}
