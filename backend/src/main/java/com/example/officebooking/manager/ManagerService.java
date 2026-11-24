package com.example.officebooking.manager;

import com.example.officebooking.audit.AuditLog;
import com.example.officebooking.audit.AuditLogRepository;
import com.example.officebooking.calendar.CalendarService;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.common.PageResponse;
import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.manager.ManagerDtos.*;
import com.example.officebooking.presence.PresenceRecord;
import com.example.officebooking.presence.PresenceRepository;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.SettingsService;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserDto;
import com.example.officebooking.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ManagerService {
    private static final int MAX_PAGE_SIZE = 200;

    private final PresenceRepository presenceRepo;
    private final UserRepository userRepo;
    private final AuditLogRepository auditRepo;
    private final SettingsService settingsService;
    private final CalendarService calendarService;
    private final Clock clock;

    public ManagerService(PresenceRepository presenceRepo, UserRepository userRepo, AuditLogRepository auditRepo,
                          SettingsService settingsService, CalendarService calendarService, Clock clock) {
        this.presenceRepo = presenceRepo;
        this.userRepo = userRepo;
        this.auditRepo = auditRepo;
        this.settingsService = settingsService;
        this.calendarService = calendarService;
        this.clock = clock;
    }

    public List<UserDto> users() {
        return userRepo.findAllByOrderByLastNameAscFirstNameAsc().stream().map(UserDto::from).toList();
    }

    /** «Сотрудники» = все включённые пользователи (руководитель тоже может работать из офиса). */
    public Dashboard dashboard(LocalDate date, String status, String query) {
        AppSettings settings = settingsService.current();
        List<User> enabled = userRepo.findAllByEnabledTrueOrderByLastNameAscFirstNameAsc();
        Map<Long, PresenceType> byUser = new HashMap<>();
        for (PresenceRecord r : presenceRepo.findAllByDateAndStatusWithUser(date, PresenceStatus.ACTIVE)) {
            byUser.put(r.getUser().getId(), r.getType());
        }

        long office = 0;
        long remote = 0;
        List<DashboardEmployee> rows = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (User u : enabled) {
            PresenceType t = byUser.get(u.getId());
            String st = t == null ? "NONE" : t.name();
            if (t == PresenceType.OFFICE) office++;
            if (t == PresenceType.REMOTE) remote++;
            boolean statusOk = status == null || status.isBlank() || status.equalsIgnoreCase(st);
            boolean queryOk = q.isEmpty() || u.fullName().toLowerCase().contains(q) || u.getEmail().toLowerCase().contains(q);
            if (statusOk && queryOk) {
                rows.add(new DashboardEmployee(u.getId(), u.fullName(), u.getEmail(), st));
            }
        }
        int capacity = settings.getOfficeCapacity();
        return new Dashboard(date, enabled.size(), office, remote, enabled.size() - office - remote,
                capacity, office, Math.max(0, capacity - office), rows);
    }

    public List<OccupancyDay> occupancy(LocalDate from, LocalDate to) {
        PeriodValidator.validate(from, to);
        int capacity = settingsService.current().getOfficeCapacity();
        Map<LocalDate, Long> office = calendarService.officeCounts(from, to);
        Map<LocalDate, Long> remote = new HashMap<>();
        for (Object[] row : presenceRepo.countByDate(PresenceType.REMOTE, PresenceStatus.ACTIVE, from, to)) {
            remote.put((LocalDate) row[0], (Long) row[1]);
        }
        List<OccupancyDay> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long occ = office.getOrDefault(d, 0L);
            result.add(new OccupancyDay(d, capacity, occ, Math.max(0, capacity - occ), remote.getOrDefault(d, 0L)));
        }
        return result;
    }

    /** Постраничный список записей заданного типа (удалённые дни или бронирования). */
    public PageResponse<ReportRow> records(PresenceType type, LocalDate from, LocalDate to, Long userId,
                                           PresenceStatus status, int page, int size, String sortBy, String direction) {
        PeriodValidator.validate(from, to);
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = switch (sortBy == null ? "date" : sortBy) {
            case "date" -> "presenceDate";
            case "createdAt" -> "createdAt";
            case "status" -> "status";
            case "user" -> "user.lastName";
            default -> throw ApiException.badRequest("BAD_SORT", "Недопустимое поле сортировки: " + sortBy);
        };
        PageRequest pageable = PageRequest.of(Math.max(0, page), clampSize(size),
                Sort.by(dir, property).and(Sort.by(Sort.Direction.DESC, "id")));

        Specification<PresenceRecord> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("user");
            }
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("type"), type));
            ps.add(cb.between(root.get("presenceDate"), from, to));
            if (userId != null) ps.add(cb.equal(root.get("user").get("id"), userId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<ReportRow> result = presenceRepo.findAll(spec, pageable).map(r -> new ReportRow(
                r.getId(), r.getUser().getId(), r.getUser().fullName(), r.getUser().getEmail(),
                r.getPresenceDate(), r.getType(), r.getStatus(), r.getCreatedAt(), r.getCancelledAt()));
        return PageResponse.of(result);
    }

    /** Сводка по сотрудникам за период: офисные дни, удалённые дни, отменённые записи. */
    public PageResponse<SummaryRow> summary(LocalDate from, LocalDate to, Long userId, int page, int size,
                                            String sortBy, String direction) {
        PeriodValidator.validate(from, to);
        Map<Long, long[]> agg = new HashMap<>(); // [office, remote, cancelled]
        for (Object[] row : presenceRepo.aggregateByUser(from, to)) {
            long[] a = agg.computeIfAbsent((Long) row[0], k -> new long[3]);
            PresenceType type = (PresenceType) row[1];
            PresenceStatus st = (PresenceStatus) row[2];
            long n = (Long) row[3];
            if (st == PresenceStatus.CANCELLED) {
                a[2] += n;
            } else if (type == PresenceType.OFFICE) {
                a[0] += n;
            } else {
                a[1] += n;
            }
        }
        List<SummaryRow> rows = userRepo.findAllByOrderByLastNameAscFirstNameAsc().stream()
                .filter(u -> userId == null || u.getId().equals(userId))
                .map(u -> {
                    long[] a = agg.getOrDefault(u.getId(), new long[3]);
                    return new SummaryRow(u.getId(), u.fullName(), u.getEmail(), a[0], a[1], a[2]);
                }).collect(Collectors.toCollection(ArrayList::new));

        Comparator<SummaryRow> cmp = switch (sortBy == null ? "userName" : sortBy) {
            case "userName" -> Comparator.comparing(SummaryRow::userName, String.CASE_INSENSITIVE_ORDER);
            case "officeDays" -> Comparator.comparingLong(SummaryRow::officeDays);
            case "remoteDays" -> Comparator.comparingLong(SummaryRow::remoteDays);
            case "cancelled" -> Comparator.comparingLong(SummaryRow::cancelled);
            default -> throw ApiException.badRequest("BAD_SORT", "Недопустимое поле сортировки: " + sortBy);
        };
        rows.sort("desc".equalsIgnoreCase(direction) ? cmp.reversed() : cmp);
        return PageResponse.of(rows, Math.max(0, page), clampSize(size));
    }

    public PageResponse<AuditDto> audit(LocalDate from, LocalDate to, Long actorUserId, int page, int size) {
        PeriodValidator.validate(from, to);
        Instant fromTs = from.atStartOfDay(clock.getZone()).toInstant();
        Instant toTs = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTs));
            ps.add(cb.lessThan(root.get("createdAt"), toTs));
            if (actorUserId != null) ps.add(cb.equal(root.get("actorUserId"), actorUserId));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<AuditLog> p = auditRepo.findAll(spec,
                PageRequest.of(Math.max(0, page), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, String> names = userRepo.findAllById(p.getContent().stream()
                        .map(AuditLog::getActorUserId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::fullName));
        return PageResponse.of(p.map(a -> new AuditDto(a.getId(), a.getActorUserId(),
                a.getActorUserId() == null ? null : names.get(a.getActorUserId()),
                a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails(), a.getCreatedAt())));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    }
}
