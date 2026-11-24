package com.example.officebooking.manager;

import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ManagerDtos {
    private ManagerDtos() {}

    public record DashboardEmployee(Long userId, String fullName, String email, String status) {}

    public record Dashboard(LocalDate date, long totalEmployees, long inOffice, long remote, long notSelected,
                            int totalSeats, long occupiedSeats, long freeSeats, List<DashboardEmployee> employees) {}

    public record OccupancyDay(LocalDate date, int capacity, long occupied, long free, long remote) {}

    public record ReportRow(Long id, Long userId, String userName, String email, LocalDate date,
                            PresenceType type, PresenceStatus status, Instant createdAt, Instant cancelledAt) {}

    public record SummaryRow(Long userId, String userName, String email, long officeDays, long remoteDays,
                             long cancelled) {}

    public record AuditDto(Long id, Long actorUserId, String actorName, String action, String entityType,
                           String entityId, String details, Instant createdAt) {}
}
