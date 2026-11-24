package com.example.officebooking.presence;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Чистые правила по датам (без доступа к БД): доступность даты и срок отмены. Все расчёты — по часам приложения. */
@Component
public class BookingRules {
    private final Clock clock;

    public BookingRules(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Причина, по которой на дату нельзя создавать записи; empty — дата доступна. */
    public Optional<String> unavailableReason(LocalDate date, boolean allowSameDay, Set<DayOfWeek> workingDays) {
        LocalDate today = today();
        if (date.isBefore(today)) {
            return Optional.of("Нельзя создавать записи на прошедшие даты");
        }
        if (date.isEqual(today) && !allowSameDay) {
            return Optional.of("Запись на текущий день недоступна — выберите будущую дату");
        }
        if (!workingDays.contains(date.getDayOfWeek())) {
            return Optional.of("Эта дата — нерабочий день");
        }
        return Optional.empty();
    }

    /**
     * Причина, по которой запись нельзя отменить; empty — отмена разрешена.
     * Крайний срок = начало выбранного дня минус deadlineHours (0 — отмена возможна до начала дня).
     */
    public Optional<String> cancellationBlockedReason(LocalDate date, int deadlineHours) {
        ZonedDateTime deadline = date.atStartOfDay(clock.getZone()).minusHours(deadlineHours);
        if (ZonedDateTime.now(clock).isAfter(deadline)) {
            String text = deadlineHours == 0
                    ? "Отмена возможна только до начала выбранного дня"
                    : "Отмена возможна не позднее чем за " + deadlineHours + " ч. до начала выбранного дня";
            return Optional.of(text);
        }
        return Optional.empty();
    }
}
