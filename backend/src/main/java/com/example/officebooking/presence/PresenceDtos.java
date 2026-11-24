package com.example.officebooking.presence;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public final class PresenceDtos {
    private PresenceDtos() {}

    public record DateRequest(@NotNull(message = "Укажите дату") LocalDate date) {}

    public record PresenceDto(Long id, LocalDate date, PresenceType type, PresenceStatus status,
                              Instant createdAt, Instant cancelledAt) {
        public static PresenceDto from(PresenceRecord r) {
            return new PresenceDto(r.getId(), r.getPresenceDate(), r.getType(), r.getStatus(),
                    r.getCreatedAt(), r.getCancelledAt());
        }
    }
}
