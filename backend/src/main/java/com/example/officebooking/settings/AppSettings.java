package com.example.officebooking.settings;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Единственная строка настроек (id = 1). */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
public class AppSettings {
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id = SINGLETON_ID;

    @Column(name = "office_capacity", nullable = false)
    private int officeCapacity;

    @Column(name = "allow_same_day_booking", nullable = false)
    private boolean allowSameDayBooking;

    @Column(name = "cancellation_deadline_hours", nullable = false)
    private int cancellationDeadlineHours;

    /** Дни недели через запятую: MONDAY,TUESDAY,... */
    @Column(name = "working_days", nullable = false)
    private String workingDays;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    public Set<DayOfWeek> workingDaysSet() {
        if (workingDays == null || workingDays.isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return Arrays.stream(workingDays.split(",")).map(String::trim).map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    public void setWorkingDaysSet(Set<DayOfWeek> days) {
        this.workingDays = days.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
}
