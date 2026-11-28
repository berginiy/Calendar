package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "work_preferences")
@Getter
@Setter
@NoArgsConstructor
public class WorkPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "pref_date", nullable = false)
    private LocalDate prefDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreferenceType preference;

    /** false — предпочтение отозвано (строка сохраняется ради first_submitted_at). */
    @Column(nullable = false)
    private boolean active = true;

    /** Момент первой подачи; не меняется при обновлении и повторной подаче. */
    @Column(name = "first_submitted_at", nullable = false)
    private Instant firstSubmittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
