package com.example.officebooking.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class PeriodValidator {
    public static final int MAX_DAYS = 366;

    private PeriodValidator() {}

    public static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("INVALID_PERIOD", "Укажите даты начала и конца периода");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_PERIOD", "Дата окончания периода раньше даты начала");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("INVALID_PERIOD", "Период не может превышать " + MAX_DAYS + " дней");
        }
    }
}
