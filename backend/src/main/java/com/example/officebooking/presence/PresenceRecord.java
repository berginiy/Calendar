package com.example.officebooking.presence;

import com.example.officebooking.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Запись присутствия сотрудника на дату: OFFICE (бронирование места) или REMOTE (удалённый день).
 * Единая таблица + частичный уникальный индекс (user_id, presence_date) WHERE status='ACTIVE'
 * гарантируют на уровне БД: у пользователя не более одной активной записи на дату.
 */
@Entity
@Table(name = "presence_records")
@Getter
@Setter
@NoArgsConstructor
public class PresenceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "presence_date", nullable = false)
    private LocalDate presenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PresenceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PresenceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;
}
