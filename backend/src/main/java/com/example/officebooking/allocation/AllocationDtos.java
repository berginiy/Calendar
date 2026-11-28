package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationTypes.ApplyStatus;
import com.example.officebooking.allocation.AllocationTypes.Outcome;
import com.example.officebooking.allocation.AllocationTypes.Policy;
import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.allocation.AllocationTypes.RunStatus;
import com.example.officebooking.allocation.AllocationTypes.Source;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AllocationDtos {
    private AllocationDtos() {}

    public record SettingsDto(boolean enabled, Policy policy, int preferenceDeadlineHours) {}

    public record SettingsRequest(
            boolean enabled,
            @NotNull(message = "Выберите политику") Policy policy,
            @Min(value = 0, message = "Срок не может быть отрицательным")
            @Max(value = 720, message = "Срок не может превышать 720 часов") int preferenceDeadlineHours) {}

    public record RunRequest(@NotNull(message = "Укажите начало периода") LocalDate from,
                             @NotNull(message = "Укажите конец периода") LocalDate to) {}

    public record AdjustRequest(
            @NotNull(message = "Укажите результат") Outcome outcome,
            @NotBlank(message = "Укажите причину изменения") @Size(max = 500, message = "Причина не длиннее 500 символов") String reason) {}

    /** Сводка по дню: сколько мест, кто что просит и сколько уже занято. */
    public record OverviewDay(LocalDate date, boolean workingDay, boolean allocatable, int capacity,
                              long occupiedOffice, long freeSeats, long officePreferences, long remotePreferences,
                              long flexiblePreferences, long noPreference) {}

    public record Overview(SettingsDto settings, long totalEmployees, Long draftRunId, List<OverviewDay> days) {}

    public record RunDto(Long id, LocalDate rangeFrom, LocalDate rangeTo, RunStatus status, Policy policy, int capacity,
                         Long createdById, String createdByName, Instant createdAt, String confirmedByName,
                         Instant confirmedAt, long total, long office, long remote, long none, long applied,
                         long skipped, long failed, long pending) {}

    public record ResultDto(Long id, Long userId, String userName, LocalDate date, PreferenceType preference,
                            Outcome outcome, Source source, String explanation, String manualReason,
                            ApplyStatus applyStatus, String applyMessage) {}

    public record DaySummary(LocalDate date, long officeWanted, long officeAssigned, long remoteAssigned,
                             long unassigned) {}

    public record RunDetail(RunDto run, List<DaySummary> days, List<ResultDto> results) {}
}
