package com.example.officebooking.settings;

import com.example.officebooking.allocation.AllocationTypes;
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

    /** Могут ли обычные сотрудники видеть имена и статусы коллег в командном календаре. */
    @Column(name = "team_visible_to_employees", nullable = false)
    private boolean teamVisibleToEmployees;

    /** Автоматическое распределение офисных и удалённых дней. По умолчанию выключено. */
    @Column(name = "allocation_enabled", nullable = false)
    private boolean allocationEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_policy", nullable = false)
    private AllocationTypes.Policy allocationPolicy = AllocationTypes.Policy.ROTATION;

    /** Предпочтения на дату можно менять не позднее чем за N часов до начала этого дня. */
    @Column(name = "preference_deadline_hours", nullable = false)
    private int preferenceDeadlineHours = 24;

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
