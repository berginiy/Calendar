package com.example.officebooking.presence;

import com.example.officebooking.audit.AuditService;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.presence.PresenceDtos.PresenceDto;
import com.example.officebooking.security.UserPrincipal;
import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.LimitRule;
import com.example.officebooking.settings.PeriodType;
import com.example.officebooking.settings.SettingsService;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Бизнес-логика бронирований и удалённых дней.
 *
 * Конкурентная безопасность (PostgreSQL, уровень READ COMMITTED):
 *  1. pg_advisory_xact_lock по пользователю — сериализует запросы одного сотрудника
 *     (проверка недельного/месячного лимита и дубликатов);
 *  2. для OFFICE — pg_advisory_xact_lock по дате — сериализует все бронирования на эту дату,
 *     поэтому COUNT(*) вместимости не может «устареть» между проверкой и вставкой;
 *  3. FOR SHARE на строке настроек — изменение вместимости дожидается идущих бронирований;
 *  4. частичный уникальный индекс в БД — последняя линия защиты от дубликатов.
 * Порядок блокировок всегда одинаков (пользователь → настройки → дата), поэтому взаимных блокировок нет.
 */
@Service
public class PresenceService {
    private static final long USER_LOCK_NAMESPACE = 1L << 40;

    @PersistenceContext
    private EntityManager em;

    private final PresenceRepository repo;
    private final UserRepository users;
    private final SettingsService settingsService;
    private final BookingRules rules;
    private final AuditService audit;
    private final Clock clock;

    public PresenceService(PresenceRepository repo, UserRepository users, SettingsService settingsService,
                           BookingRules rules, AuditService audit, Clock clock) {
        this.repo = repo;
        this.users = users;
        this.settingsService = settingsService;
        this.rules = rules;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PresenceDto create(Long userId, LocalDate date, PresenceType type) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));

        advisoryLock(USER_LOCK_NAMESPACE + userId);
        // после получения блокировки перечитываем пользователя: его могли отключить, пока мы ждали
        em.refresh(user);
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Учётная запись отключена");
        }
        AppSettings settings = settingsService.lockedForBooking();

        rules.unavailableReason(date, settings.isAllowSameDayBooking(), settings.workingDaysSet())
                .ifPresent(reason -> {
                    throw ApiException.conflict("DATE_NOT_AVAILABLE", reason);
                });

        if (type == PresenceType.OFFICE) {
            advisoryLock(date.toEpochDay());
        }

        repo.findByUser_IdAndPresenceDateAndStatus(userId, date, PresenceStatus.ACTIVE).ifPresent(existing -> {
            if (existing.getType() == type) {
                throw ApiException.conflict("DUPLICATE",
                        type == PresenceType.OFFICE
                                ? "На эту дату у вас уже есть бронирование места"
                                : "Эта дата уже отмечена как удалённая работа");
            }
            throw ApiException.conflict("ALREADY_HAS_OTHER_STATUS",
                    existing.getType() == PresenceType.OFFICE
                            ? "На эту дату у вас уже забронировано место в офисе. Сначала отмените бронирование"
                            : "Эта дата уже отмечена как удалённая работа. Сначала отмените отметку");
        });

        if (type == PresenceType.OFFICE) {
            long occupied = repo.countByPresenceDateAndTypeAndStatus(date, PresenceType.OFFICE, PresenceStatus.ACTIVE);
            if (occupied >= settings.getOfficeCapacity()) {
                throw ApiException.conflict("NO_FREE_SEATS",
                        "На выбранную дату свободных мест нет (" + occupied + " из " + settings.getOfficeCapacity() + ")");
            }
        } else {
            checkRemoteLimits(userId, date);
        }

        PresenceRecord record = new PresenceRecord();
        record.setUser(user);
        record.setPresenceDate(date);
        record.setType(type);
        record.setStatus(PresenceStatus.ACTIVE);
        record.setCreatedAt(Instant.now(clock));
        try {
            repo.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DUPLICATE", "На эту дату у вас уже есть активная запись");
        }

        audit.log(userId, type == PresenceType.OFFICE ? "BOOKING_CREATED" : "REMOTE_DAY_CREATED",
                type.name(), record.getId(), "date=" + date);
        return PresenceDto.from(record);
    }

    @Transactional
    public PresenceDto cancel(UserPrincipal actor, Long recordId, PresenceType type) {
        PresenceRecord record = repo.findByIdForUpdate(recordId)
                .filter(r -> r.getType() == type)
                .orElseThrow(() -> ApiException.notFound("Запись не найдена"));

        boolean own = record.getUser().getId().equals(actor.id());
        if (!own && !actor.isManager()) {
            throw ApiException.forbidden("Нельзя отменять чужие записи");
        }
        if (record.getStatus() == PresenceStatus.CANCELLED) {
            throw ApiException.conflict("ALREADY_CANCELLED", "Запись уже отменена");
        }
        if (own) {
            // руководитель при администрировании чужих записей срок отмены не соблюдает
            rules.cancellationBlockedReason(record.getPresenceDate(), settingsService.current().getCancellationDeadlineHours())
                    .ifPresent(reason -> {
                        throw ApiException.conflict("CANCELLATION_NOT_ALLOWED", reason);
                    });
        }

        record.setStatus(PresenceStatus.CANCELLED);
        record.setCancelledAt(Instant.now(clock));
        record.setCancelledByUserId(actor.id());
        repo.save(record);

        String action;
        if (own) {
            action = type == PresenceType.OFFICE ? "BOOKING_CANCELLED" : "REMOTE_DAY_CANCELLED";
        } else {
            action = type == PresenceType.OFFICE ? "ADMIN_CANCELLED_BOOKING" : "ADMIN_CANCELLED_REMOTE_DAY";
        }
        audit.log(actor.id(), action, type.name(), record.getId(),
                "date=" + record.getPresenceDate() + ", ownerUserId=" + record.getUser().getId());
        return PresenceDto.from(record);
    }

    /** Берёт ту же блокировку пользователя, что и создание записей (до конца текущей транзакции). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUser(Long userId) {
        advisoryLock(USER_LOCK_NAMESPACE + userId);
    }

    /** Отменяет все активные записи пользователя начиная с даты (при деактивации). История сохраняется. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelFutureForUser(Long userId, Long actorId, LocalDate from) {
        advisoryLock(USER_LOCK_NAMESPACE + userId);
        List<PresenceRecord> records = repo.findActiveForUserFrom(userId, PresenceStatus.ACTIVE, from);
        Instant now = Instant.now(clock);
        for (PresenceRecord r : records) {
            r.setStatus(PresenceStatus.CANCELLED);
            r.setCancelledAt(now);
            r.setCancelledByUserId(actorId);
        }
        repo.saveAll(records);
        if (!records.isEmpty()) {
            audit.log(actorId, "ADMIN_CANCELLED_RECORDS", "User", userId,
                    "count=" + records.size() + ", from=" + from);
        }
        return records.size();
    }

    @Transactional(readOnly = true)
    public List<PresenceDto> myRecords(Long userId, PresenceType type, LocalDate from, LocalDate to) {
        PeriodValidator.validate(from, to);
        return repo.findByUser_IdAndTypeAndPresenceDateBetweenOrderByPresenceDateDescIdDesc(userId, type, from, to)
                .stream().map(PresenceDto::from).toList();
    }

    private void checkRemoteLimits(Long userId, LocalDate date) {
        Map<PeriodType, LimitRule> active = settingsService.activeRules();
        LimitRule weekRule = active.get(PeriodType.WEEKLY);
        LimitRule monthRule = active.get(PeriodType.MONTHLY);
        LocalDate today = rules.today();

        long weekUsed = 0;
        boolean weekCurrent = false;
        if (weekRule != null) {
            LocalDate from = weekStart(date);
            weekUsed = repo.countByUser_IdAndTypeAndStatusAndPresenceDateBetween(
                    userId, PresenceType.REMOTE, PresenceStatus.ACTIVE, from, from.plusDays(6));
            weekCurrent = !today.isBefore(from) && !today.isAfter(from.plusDays(6));
        }
        long monthUsed = 0;
        boolean monthCurrent = false;
        if (monthRule != null) {
            LocalDate from = date.withDayOfMonth(1);
            LocalDate to = date.with(TemporalAdjusters.lastDayOfMonth());
            monthUsed = repo.countByUser_IdAndTypeAndStatusAndPresenceDateBetween(
                    userId, PresenceType.REMOTE, PresenceStatus.ACTIVE, from, to);
            monthCurrent = today.getYear() == date.getYear() && today.getMonth() == date.getMonth();
        }

        List<String> violations = RemoteLimitPolicy.violations(weekRule, weekUsed, weekCurrent,
                monthRule, monthUsed, monthCurrent);
        if (!violations.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "REMOTE_LIMIT_EXCEEDED",
                    String.join(". ", violations), violations);
        }
    }

    /** Понедельник календарной недели, в которую попадает дата. */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void advisoryLock(long key) {
        // подзапрос нужен, чтобы не читать результат типа void (Hibernate не умеет его маппить)
        em.createNativeQuery("select 1 from (select pg_advisory_xact_lock(:k)) as t")
                .setParameter("k", key)
                .getSingleResult();
    }
}
