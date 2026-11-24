package com.example.officebooking.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

public final class SettingsDtos {
    private SettingsDtos() {}

    public record SettingsRequest(
            @Min(value = 1, message = "Количество мест должно быть больше нуля")
            @Max(value = 1000, message = "Количество мест не может превышать 1000") int officeCapacity,
            boolean allowSameDayBooking,
            @Min(value = 0, message = "Срок отмены не может быть отрицательным")
            @Max(value = 720, message = "Срок отмены не может превышать 720 часов") int cancellationDeadlineHours,
            @NotEmpty(message = "Выберите хотя бы один рабочий день") Set<DayOfWeek> workingDays) {}

    public record SettingsResponse(int officeCapacity, boolean allowSameDayBooking, int cancellationDeadlineHours,
                                   List<DayOfWeek> workingDays, String timezone) {
        public static SettingsResponse from(AppSettings s) {
            return new SettingsResponse(s.getOfficeCapacity(), s.isAllowSameDayBooking(),
                    s.getCancellationDeadlineHours(), s.workingDaysSet().stream().sorted().toList(), s.getTimezone());
        }
    }

    public record LimitRuleDto(
            @NotNull(message = "Укажите тип периода") PeriodType periodType,
            @Min(value = 0, message = "Лимит не может быть отрицательным")
            @Max(value = 366, message = "Лимит слишком велик") int maxDays,
            boolean enabled) {
        public static LimitRuleDto from(LimitRule r) {
            return new LimitRuleDto(r.getPeriodType(), r.getMaxDays(), r.isEnabled());
        }
    }
}
