package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationTypes.ApplyStatus;
import com.example.officebooking.allocation.AllocationTypes.Outcome;
import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.allocation.AllocationTypes.Source;
import com.example.officebooking.user.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "allocation_results")
@Getter
@Setter
@NoArgsConstructor
public class AllocationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private AllocationRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "result_date", nullable = false)
    private LocalDate resultDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreferenceType preference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(name = "manual_reason", length = 500)
    private String manualReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_status", nullable = false)
    private ApplyStatus applyStatus;

    @Column(name = "apply_message", length = 500)
    private String applyMessage;
}
