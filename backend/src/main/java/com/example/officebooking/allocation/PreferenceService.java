package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationTypes.ApplyStatus;
import com.example.officebooking.allocation.AllocationTypes.Outcome;
import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.allocation.AllocationTypes.RunStatus;
import com.example.officebooking.allocation.AllocationTypes.Source;
import com.example.officebooking.common.AdvisoryLocks;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.presence.BookingRules;
import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.SettingsService;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Предпочтения сотрудников на будущие даты. Идентификатор сотрудника всегда берётся из токена.
 * Предпочтение — не гарантия: оно только входные данные для распределения.
 */
@Service
public class PreferenceService {
    public static final int MAX_DAYS = 62;
    private static final long PREF_LOCK_NS = 4L << 40;
    private static final List<RunStatus> LOCKING = List.of(RunStatus.CONFIRMED, RunStatus.APPLYING);
    private static final List<RunStatus> VISIBLE = List.of(RunStatus.DRAFT, RunStatus.APPLYING, RunStatus.CONFIRMED);

    /**
     * status: PENDING — ожидает распределения; PRELIMINARY — есть предварительный результат (ещё не подтверждён);
     * SATISFIED — учтено; ASSIGNED — назначено иначе, чем запрошено; NOT_SATISFIED — не удовлетворено.
     * Для даты без предпочтения status = null.
     */
    public record PreferenceDay(LocalDate date, boolean workingDay, Long preferenceId, PreferenceType preference,
                                String status, Outcome outcome, boolean confirmed, String explanation,
                                boolean editable, String editableReason) {}

    public record PreferencesResponse(boolean enabled, int deadlineHours, List<PreferenceDay> days) {}

    public record PreferenceRequest(@NotNull(message = "Укажите дату") LocalDate date,
                                    @NotNull(message = "Укажите предпочтение") PreferenceType preference) {}

    public record UpdateRequest(@NotNull(message = "Укажите предпочтение") PreferenceType preference) {}

    /** preference == null — отозвать предпочтение на эту дату. */
    public record BulkItem(@NotNull(message = "Укажите дату") LocalDate date, PreferenceType preference) {}

    public record BulkRequest(@NotEmpty(message = "Список не может быть пустым")
                              @Size(max = MAX_DAYS, message = "Не более 62 дат за раз") List<@Valid BulkItem> items) {}

    private final WorkPreferenceRepository repo;
    private final AllocationResultRepository results;
    private final UserRepository users;
    private final SettingsService settingsService;
    private final BookingRules rules;
    private final AdvisoryLocks locks;
    private final Clock clock;

    public PreferenceService(WorkPreferenceRepository repo, AllocationResultRepository results, UserRepository users,
                             SettingsService settingsService, BookingRules rules, AdvisoryLocks locks, Clock clock) {
        this.repo = repo;
        this.results = results;
        this.users = users;
        this.settingsService = settingsService;
        this.rules = rules;
        this.locks = locks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse list(Long userId, LocalDate from, LocalDate to) {
        validatePeriod(from, to);
        AppSettings s = settingsService.current();
        return new PreferencesResponse(s.isAllocationEnabled(), s.getPreferenceDeadlineHours(),
                buildDays(userId, from, to, s));
    }

    @Transactional
    public PreferenceDay create(Long userId, LocalDate date, PreferenceType preference) {
        locks.lock(PREF_LOCK_NS + userId);
        AppSettings s = settingsService.current();
        ensureEditable(userId, date, s);
        Optional<WorkPreference> existing = repo.findByUser_IdAndPrefDate(userId, date);
        if (existing.isPresent() && existing.get().isActive()) {
            throw ApiException.conflict("DUPLICATE",
                    "Предпочтение на эту дату уже указано — измените его вместо создания нового");
        }
        save(userId, date, preference, existing.orElse(null));
        return day(userId, date, s);
    }

    @Transactional
    public PreferenceDay update(Long userId, Long id, PreferenceType preference) {
        locks.lock(PREF_LOCK_NS + userId);
        WorkPreference p = owned(userId, id);
        AppSettings s = settingsService.current();
        ensureEditable(userId, p.getPrefDate(), s);
        p.setPreference(preference);
        p.setUpdatedAt(Instant.now(clock)); // first_submitted_at не меняется: обновление преимущества не даёт
        repo.save(p);
        return day(userId, p.getPrefDate(), s);
    }

    @Transactional
    public PreferenceDay withdraw(Long userId, Long id) {
        locks.lock(PREF_LOCK_NS + userId);
        WorkPreference p = owned(userId, id);
        AppSettings s = settingsService.current();
        ensureEditable(userId, p.getPrefDate(), s);
        p.setActive(false);
        p.setUpdatedAt(Instant.now(clock));
        repo.save(p);
        return day(userId, p.getPrefDate(), s);
    }

    /** Массовое задание предпочтений (все или ни одного): удобно для планирования недели. */
    @Transactional
    public List<PreferenceDay> bulk(Long userId, List<BulkItem> items) {
        Set<LocalDate> seen = new HashSet<>();
        for (BulkItem i : items) {
            if (!seen.add(i.date())) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Дата " + i.date() + " указана несколько раз");
            }
        }
        locks.lock(PREF_LOCK_NS + userId);
        AppSettings s = settingsService.current();
        List<PreferenceDay> out = new ArrayList<>();
        for (BulkItem i : items) {
            Optional<WorkPreference> existing = repo.findByUser_IdAndPrefDate(userId, i.date());
            if (i.preference() == null) {
                if (existing.isPresent() && existing.get().isActive()) {
                    ensureEditable(userId, i.date(), s);
                    existing.get().setActive(false);
                    existing.get().setUpdatedAt(Instant.now(clock));
                    repo.save(existing.get());
                }
            } else {
                ensureEditable(userId, i.date(), s);
                save(userId, i.date(), i.preference(), existing.orElse(null));
            }
            out.add(day(userId, i.date(), s));
        }
        return out;
    }

    // ---------------------------------------------------------------

    private void save(Long userId, LocalDate date, PreferenceType preference, WorkPreference existing) {
        Instant now = Instant.now(clock);
        WorkPreference p = existing;
        if (p == null) {
            User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
            p = new WorkPreference();
            p.setUser(user);
            p.setPrefDate(date);
            p.setFirstSubmittedAt(now);
        }
        p.setPreference(preference);
        p.setActive(true);
        p.setUpdatedAt(now);
        try {
            repo.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DUPLICATE", "Предпочтение на эту дату уже указано");
        }
    }

    private WorkPreference owned(Long userId, Long id) {
        WorkPreference p = repo.findById(id).orElseThrow(() -> ApiException.notFound("Предпочтение не найдено"));
        if (!p.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Нельзя менять чужие предпочтения");
        }
        if (!p.isActive()) {
            throw ApiException.notFound("Предпочтение не найдено");
        }
        return p;
    }

    private void ensureEditable(Long userId, LocalDate date, AppSettings s) {
        boolean locked = results.countForUserOnDate(userId, date, LOCKING) > 0;
        String reason = editableReason(date, s, locked).orElse(null);
        if (reason != null) {
            throw ApiException.conflict(s.isAllocationEnabled() ? "PREFERENCE_NOT_EDITABLE" : "FEATURE_DISABLED", reason);
        }
    }

    Optional<String> editableReason(LocalDate date, AppSettings s, boolean lockedByAllocation) {
        if (!s.isAllocationEnabled()) {
            return Optional.of("Автоматическое распределение выключено");
        }
        if (!s.workingDaysSet().contains(date.getDayOfWeek())) {
            return Optional.of("Эта дата — нерабочий день");
        }
        if (!date.isAfter(rules.today())) {
            return Optional.of("Предпочтение можно указать только на будущие даты");
        }
        ZonedDateTime deadline = date.atStartOfDay(clock.getZone()).minusHours(s.getPreferenceDeadlineHours());
        if (ZonedDateTime.now(clock).isAfter(deadline)) {
            return Optional.of(s.getPreferenceDeadlineHours() == 0
                    ? "Срок изменения предпочтений истёк: он действует до начала выбранного дня"
                    : "Срок изменения предпочтений истёк: он действует не позднее чем за "
                    + s.getPreferenceDeadlineHours() + " ч. до начала выбранного дня");
        }
        if (lockedByAllocation) {
            return Optional.of("Распределение на эту дату уже подтверждено — предпочтение изменить нельзя");
        }
        return Optional.empty();
    }

    private PreferenceDay day(Long userId, LocalDate date, AppSettings s) {
        return buildDays(userId, date, date, s).get(0);
    }

    private List<PreferenceDay> buildDays(Long userId, LocalDate from, LocalDate to, AppSettings s) {
        Map<LocalDate, WorkPreference> prefs = new HashMap<>();
        for (WorkPreference p : repo.findByUser_IdAndPrefDateBetween(userId, from, to)) {
            if (p.isActive()) prefs.put(p.getPrefDate(), p);
        }
        Map<LocalDate, AllocationResult> best = bestResults(userId, from, to);

        List<PreferenceDay> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            WorkPreference p = prefs.get(d);
            AllocationResult r = best.get(d);
            boolean locked = r != null && LOCKING.contains(r.getRun().getStatus());
            Optional<String> reason = editableReason(d, s, locked);

            String status = null;
            Outcome outcome = null;
            boolean confirmed = false;
            String explanation = null;
            if (p != null) {
                status = "PENDING";
                if (r != null) {
                    outcome = r.getOutcome();
                    confirmed = r.getRun().getStatus() == RunStatus.CONFIRMED;
                    explanation = r.getSource() == Source.MANUAL
                            ? "Назначение скорректировано руководителем."
                            : r.getExplanation();
                    status = statusOf(r, p.getPreference(), confirmed);
                    if (confirmed && r.getApplyStatus() != ApplyStatus.APPLIED && r.getSource() != Source.EXISTING
                            && r.getOutcome() != Outcome.NONE && r.getApplyMessage() != null) {
                        explanation = explanation + " Не применено: " + r.getApplyMessage();
                    }
                }
            }
            days.add(new PreferenceDay(d, s.workingDaysSet().contains(d.getDayOfWeek()),
                    p == null ? null : p.getId(), p == null ? null : p.getPreference(), status, outcome, confirmed,
                    explanation, reason.isEmpty(), reason.orElse(null)));
        }
        return days;
    }

    private static String statusOf(AllocationResult r, PreferenceType pref, boolean confirmed) {
        if (!confirmed) {
            return "PRELIMINARY";
        }
        boolean realized = r.getSource() == Source.EXISTING || r.getApplyStatus() == ApplyStatus.APPLIED;
        if (r.getOutcome() == Outcome.NONE || !realized) {
            return "NOT_SATISFIED";
        }
        boolean matches = pref == PreferenceType.FLEXIBLE
                || (pref == PreferenceType.OFFICE && r.getOutcome() == Outcome.OFFICE)
                || (pref == PreferenceType.REMOTE && r.getOutcome() == Outcome.REMOTE);
        return matches ? "SATISFIED" : "ASSIGNED";
    }

    /** Для каждой даты — самый «весомый» результат: подтверждённый > применяемый > черновик, затем новее. */
    private Map<LocalDate, AllocationResult> bestResults(Long userId, LocalDate from, LocalDate to) {
        Map<LocalDate, AllocationResult> best = new HashMap<>();
        for (AllocationResult r : results.findForUser(userId, from, to, VISIBLE)) {
            AllocationResult cur = best.get(r.getResultDate());
            if (cur == null || rank(r) > rank(cur)
                    || (rank(r) == rank(cur) && r.getRun().getId() > cur.getRun().getId())) {
                best.put(r.getResultDate(), r);
            }
        }
        return best;
    }

    private static int rank(AllocationResult r) {
        return switch (r.getRun().getStatus()) {
            case CONFIRMED -> 3;
            case APPLYING -> 2;
            default -> 1;
        };
    }

    private static void validatePeriod(LocalDate from, LocalDate to) {
        PeriodValidator.validate(from, to);
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("INVALID_PERIOD", "Период не может превышать " + MAX_DAYS + " дней");
        }
    }
}
