package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationAlgorithm.Candidate;
import com.example.officebooking.allocation.AllocationAlgorithm.Day;
import com.example.officebooking.allocation.AllocationAlgorithm.Decision;
import com.example.officebooking.allocation.AllocationAlgorithm.Params;
import com.example.officebooking.allocation.AllocationDtos.*;
import com.example.officebooking.allocation.AllocationTypes.ApplyStatus;
import com.example.officebooking.allocation.AllocationTypes.Outcome;
import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.allocation.AllocationTypes.RunStatus;
import com.example.officebooking.allocation.AllocationTypes.Source;
import com.example.officebooking.audit.AuditService;
import com.example.officebooking.common.AdvisoryLocks;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.notification.NotificationService;
import com.example.officebooking.presence.BookingRules;
import com.example.officebooking.presence.PresenceRecord;
import com.example.officebooking.presence.PresenceRepository;
import com.example.officebooking.presence.PresenceService;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.presence.RemoteLimitPolicy;
import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.AppSettingsRepository;
import com.example.officebooking.settings.LimitRule;
import com.example.officebooking.settings.PeriodType;
import com.example.officebooking.settings.SettingsService;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Умное распределение. Принципы надёжности:
 *  - запуск только создаёт ЧЕРНОВИК (результаты в отдельных таблицах), реальные записи не трогает;
 *  - реальные записи появляются только при подтверждении, и каждая — через обычные проверки PresenceService;
 *  - уже существующие записи сотрудников никогда не изменяются и не отменяются;
 *  - одновременно возможен только один незавершённый запуск на пересекающийся период.
 */
@Service
public class AllocationService {
    public static final int MAX_DAYS = 31;
    private static final long ALLOCATION_LOCK = 3L << 40;
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AllocationRunRepository runRepo;
    private final AllocationResultRepository resultRepo;
    private final WorkPreferenceRepository prefRepo;
    private final PresenceRepository presenceRepo;
    private final UserRepository users;
    private final SettingsService settingsService;
    private final AppSettingsRepository settingsRepo;
    private final BookingRules rules;
    private final AdvisoryLocks locks;
    private final AuditService audit;
    private final NotificationService notifications;
    private final AllocationApplier applier;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AllocationService(AllocationRunRepository runRepo, AllocationResultRepository resultRepo,
                             WorkPreferenceRepository prefRepo, PresenceRepository presenceRepo, UserRepository users,
                             SettingsService settingsService, AppSettingsRepository settingsRepo, BookingRules rules,
                             AdvisoryLocks locks, AuditService audit, NotificationService notifications,
                             AllocationApplier applier, PlatformTransactionManager txManager, Clock clock) {
        this.runRepo = runRepo;
        this.resultRepo = resultRepo;
        this.prefRepo = prefRepo;
        this.presenceRepo = presenceRepo;
        this.users = users;
        this.settingsService = settingsService;
        this.settingsRepo = settingsRepo;
        this.rules = rules;
        this.locks = locks;
        this.audit = audit;
        this.notifications = notifications;
        this.applier = applier;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    // ---------------------------------------------------------------- настройки

    @Transactional(readOnly = true)
    public SettingsDto settings() {
        return toDto(settingsService.current());
    }

    @Transactional
    public SettingsDto updateSettings(Long actorId, SettingsRequest req) {
        AppSettings s = settingsRepo.findExclusiveLocked()
                .orElseThrow(() -> new IllegalStateException("Настройки приложения не инициализированы"));
        String before = "enabled=" + s.isAllocationEnabled() + ", policy=" + s.getAllocationPolicy()
                + ", deadlineHours=" + s.getPreferenceDeadlineHours();
        s.setAllocationEnabled(req.enabled());
        s.setAllocationPolicy(req.policy());
        s.setPreferenceDeadlineHours(req.preferenceDeadlineHours());
        s.setUpdatedAt(Instant.now(clock));
        s.setUpdatedByUserId(actorId);
        settingsRepo.save(s);
        audit.log(actorId, "ALLOCATION_SETTINGS_CHANGED", "AppSettings", s.getId(),
                "before: " + before + " | after: enabled=" + req.enabled() + ", policy=" + req.policy()
                        + ", deadlineHours=" + req.preferenceDeadlineHours());
        return toDto(s);
    }

    private static SettingsDto toDto(AppSettings s) {
        return new SettingsDto(s.isAllocationEnabled(), s.getAllocationPolicy(), s.getPreferenceDeadlineHours());
    }

    // ---------------------------------------------------------------- сводка

    @Transactional(readOnly = true)
    public Overview overview(LocalDate from, LocalDate to) {
        validatePeriod(from, to);
        AppSettings s = settingsService.current();
        long total = users.countByEnabledTrue();
        Map<LocalDate, Long> office = new HashMap<>();
        for (Object[] row : presenceRepo.countByDate(PresenceType.OFFICE, PresenceStatus.ACTIVE, from, to)) {
            office.put((LocalDate) row[0], (Long) row[1]);
        }
        Map<LocalDate, long[]> prefs = new HashMap<>(); // [office, remote, flexible]
        for (Object[] row : prefRepo.countByDateAndPreference(from, to)) {
            long[] a = prefs.computeIfAbsent((LocalDate) row[0], k -> new long[3]);
            a[((PreferenceType) row[1]).ordinal()] += (Long) row[2];
        }
        List<AllocationRun> open = runRepo.findOverlapping(List.of(RunStatus.DRAFT, RunStatus.APPLYING), from, to);
        LocalDate today = rules.today();

        List<OverviewDay> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            boolean working = s.workingDaysSet().contains(d.getDayOfWeek());
            boolean allocatable = d.isAfter(today)
                    && rules.unavailableReason(d, s.isAllowSameDayBooking(), s.workingDaysSet()).isEmpty();
            long occupied = office.getOrDefault(d, 0L);
            long[] p = prefs.getOrDefault(d, new long[3]);
            long noPref = working ? Math.max(0, total - p[0] - p[1] - p[2]) : 0;
            days.add(new OverviewDay(d, working, allocatable, s.getOfficeCapacity(), occupied,
                    Math.max(0, s.getOfficeCapacity() - occupied), p[0], p[1], p[2], noPref));
        }
        return new Overview(toDto(s), total, open.isEmpty() ? null : open.get(0).getId(), days);
    }

    // ---------------------------------------------------------------- запуск (черновик)

    @Transactional
    public RunDetail run(Long actorId, LocalDate from, LocalDate to) {
        validatePeriod(from, to);
        locks.lock(ALLOCATION_LOCK);
        AppSettings s = settingsService.lockedForBooking();
        if (!s.isAllocationEnabled()) {
            throw ApiException.conflict("FEATURE_DISABLED", "Автоматическое распределение выключено в настройках");
        }
        if (!runRepo.findOverlapping(List.of(RunStatus.DRAFT, RunStatus.APPLYING), from, to).isEmpty()) {
            throw ApiException.conflict("DRAFT_EXISTS",
                    "На этот период уже есть незавершённое распределение. Подтвердите или отклоните его");
        }
        List<LocalDate> dates = new ArrayList<>();
        LocalDate today = rules.today();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.isAfter(today)
                    && rules.unavailableReason(d, s.isAllowSameDayBooking(), s.workingDaysSet()).isEmpty()) {
                dates.add(d);
            }
        }
        if (dates.isEmpty()) {
            throw ApiException.conflict("NO_DAYS", "В выбранном периоде нет дней, доступных для распределения");
        }
        Set<LocalDate> dateSet = new HashSet<>(dates);
        LocalDate minDate = dates.get(0);
        LocalDate maxDate = dates.get(dates.size() - 1);

        List<WorkPreference> prefs = prefRepo.findActiveInRangeWithUser(minDate, maxDate).stream()
                .filter(p -> p.getUser().isEnabled() && dateSet.contains(p.getPrefDate())).toList();
        if (prefs.isEmpty()) {
            throw ApiException.conflict("NO_PREFERENCES", "На выбранный период никто не подал предпочтения");
        }
        Map<Long, User> userById = new HashMap<>();
        Map<LocalDate, List<Candidate>> candidates = new HashMap<>();
        for (WorkPreference p : prefs) {
            userById.put(p.getUser().getId(), p.getUser());
            candidates.computeIfAbsent(p.getPrefDate(), k -> new ArrayList<>())
                    .add(new Candidate(p.getUser().getId(), p.getPreference(), p.getFirstSubmittedAt()));
        }

        // существующие активные записи и уже использованные удалённые дни (недели/месяцы берём целиком)
        LocalDate wFrom = PresenceService.weekStart(minDate);
        LocalDate mFrom = minDate.withDayOfMonth(1);
        LocalDate wTo = PresenceService.weekStart(maxDate).plusDays(6);
        LocalDate mTo = maxDate.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate usageFrom = wFrom.isBefore(mFrom) ? wFrom : mFrom;
        LocalDate usageTo = wTo.isAfter(mTo) ? wTo : mTo;
        Map<LocalDate, Map<Long, PresenceType>> existingByDate = new HashMap<>();
        Map<LocalDate, Long> officeCount = new HashMap<>();
        Map<Long, Map<LocalDate, Integer>> weekRemote = new HashMap<>();
        Map<Long, Map<YearMonth, Integer>> monthRemote = new HashMap<>();
        for (PresenceRecord r : presenceRepo.findAllByStatusInRangeWithUser(PresenceStatus.ACTIVE, usageFrom, usageTo)) {
            Long uid = r.getUser().getId();
            LocalDate d = r.getPresenceDate();
            if (dateSet.contains(d)) {
                existingByDate.computeIfAbsent(d, k -> new HashMap<>()).put(uid, r.getType());
                if (r.getType() == PresenceType.OFFICE) officeCount.merge(d, 1L, Long::sum);
            }
            if (r.getType() == PresenceType.REMOTE) {
                weekRemote.computeIfAbsent(uid, k -> new HashMap<>()).merge(PresenceService.weekStart(d), 1, Integer::sum);
                monthRemote.computeIfAbsent(uid, k -> new HashMap<>()).merge(YearMonth.from(d), 1, Integer::sum);
            }
        }

        // история для ротации: сколько офисных предпочтений ранее не было удовлетворено
        Map<Long, Integer> missed = new HashMap<>();
        for (Object[] row : resultRepo.findFairnessHistory(RunStatus.CONFIRMED, PreferenceType.OFFICE, Source.EXISTING)) {
            boolean satisfied = row[1] == Outcome.OFFICE && row[2] == ApplyStatus.APPLIED;
            if (!satisfied) missed.merge((Long) row[0], 1, Integer::sum);
        }

        Map<PeriodType, LimitRule> active = settingsService.activeRules();
        Integer weeklyMax = active.containsKey(PeriodType.WEEKLY) ? active.get(PeriodType.WEEKLY).getMaxDays() : null;
        Integer monthlyMax = active.containsKey(PeriodType.MONTHLY) ? active.get(PeriodType.MONTHLY).getMaxDays() : null;

        List<Day> days = new ArrayList<>();
        for (LocalDate d : dates) {
            int free = (int) Math.max(0, s.getOfficeCapacity() - officeCount.getOrDefault(d, 0L));
            days.add(new Day(d, s.getOfficeCapacity(), free, existingByDate.getOrDefault(d, Map.of()),
                    candidates.getOrDefault(d, List.of())));
        }
        List<Decision> decisions = AllocationAlgorithm.allocate(days, new Params(s.getAllocationPolicy(), weeklyMax,
                monthlyMax, today, missed, weekRemote, monthRemote));

        AllocationRun run = new AllocationRun();
        run.setRangeFrom(from);
        run.setRangeTo(to);
        run.setStatus(RunStatus.DRAFT);
        run.setPolicy(s.getAllocationPolicy());
        run.setCapacity(s.getOfficeCapacity());
        run.setCreatedById(actorId);
        run.setCreatedAt(Instant.now(clock));
        runRepo.saveAndFlush(run);

        List<AllocationResult> saved = new ArrayList<>();
        for (Decision dec : decisions) {
            AllocationResult r = new AllocationResult();
            r.setRun(run);
            r.setUser(userById.get(dec.userId()));
            r.setResultDate(dec.date());
            r.setPreference(dec.preference());
            r.setOutcome(dec.outcome());
            r.setSource(dec.source());
            r.setExplanation(dec.explanation());
            if (dec.source() == Source.EXISTING) {
                r.setApplyStatus(ApplyStatus.SKIPPED);
                r.setApplyMessage("Запись уже существует");
            } else if (dec.outcome() == Outcome.NONE) {
                r.setApplyStatus(ApplyStatus.SKIPPED);
                r.setApplyMessage("Назначение не требуется");
            } else {
                r.setApplyStatus(ApplyStatus.PENDING);
            }
            saved.add(r);
        }
        resultRepo.saveAll(saved);
        audit.log(actorId, "ALLOCATION_RUN_CREATED", "AllocationRun", run.getId(),
                "range=" + from + ".." + to + ", policy=" + run.getPolicy() + ", results=" + saved.size());
        return detail(run, saved);
    }

    // ---------------------------------------------------------------- чтение

    @Transactional(readOnly = true)
    public RunDetail get(Long runId) {
        AllocationRun run = runRepo.findById(runId).orElseThrow(() -> ApiException.notFound("Запуск не найден"));
        return detail(run, resultRepo.findByRunWithUser(runId));
    }

    @Transactional(readOnly = true)
    public List<RunDto> runs() {
        List<AllocationRun> list = runRepo.findTop50ByOrderByIdDesc();
        if (list.isEmpty()) return List.of();
        Map<Long, Counts> counts = new HashMap<>();
        for (Object[] row : resultRepo.aggregateByRuns(list.stream().map(AllocationRun::getId).toList())) {
            counts.computeIfAbsent((Long) row[0], k -> new Counts()).add((Outcome) row[1], (ApplyStatus) row[2], (Long) row[3]);
        }
        Map<Long, String> names = names(list.stream().flatMap(r -> java.util.stream.Stream.of(r.getCreatedById(), r.getConfirmedById()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return list.stream().map(r -> runDto(r, names, counts.getOrDefault(r.getId(), new Counts()))).toList();
    }

    // ---------------------------------------------------------------- ручная корректировка черновика

    @Transactional
    public ResultDto adjust(Long actorId, Long runId, Long resultId, Outcome outcome, String reason) {
        AllocationRun run = runRepo.findByIdForUpdate(runId).orElseThrow(() -> ApiException.notFound("Запуск не найден"));
        if (run.getStatus() != RunStatus.DRAFT) {
            throw ApiException.conflict("RUN_NOT_EDITABLE", "Менять можно только черновик распределения");
        }
        AllocationResult r = resultRepo.findById(resultId).orElseThrow(() -> ApiException.notFound("Результат не найден"));
        if (!r.getRun().getId().equals(runId)) {
            throw ApiException.notFound("Результат не найден");
        }
        if (r.getSource() == Source.EXISTING) {
            throw ApiException.conflict("EXISTING_IMMUTABLE", "Существующую запись распределение не меняет");
        }
        AppSettings s = settingsService.current();
        Long userId = r.getUser().getId();
        LocalDate date = r.getResultDate();

        if (outcome != Outcome.NONE) {
            rules.unavailableReason(date, s.isAllowSameDayBooking(), s.workingDaysSet()).ifPresent(reason0 -> {
                throw ApiException.conflict("DATE_NOT_AVAILABLE", reason0);
            });
            presenceRepo.findByUser_IdAndPresenceDateAndStatus(userId, date, PresenceStatus.ACTIVE).ifPresent(x -> {
                throw ApiException.conflict("DUPLICATE", "У сотрудника на эту дату уже появилась запись");
            });
        }
        if (outcome == Outcome.OFFICE) {
            long occupied = presenceRepo.countByPresenceDateAndTypeAndStatus(date, PresenceType.OFFICE, PresenceStatus.ACTIVE);
            long draftOffice = resultRepo.countByRun_IdAndResultDateAndOutcomeAndSourceNotAndIdNot(
                    runId, date, Outcome.OFFICE, Source.EXISTING, resultId);
            if (occupied + draftOffice + 1 > s.getOfficeCapacity()) {
                throw ApiException.conflict("NO_FREE_SEATS", "На эту дату все места уже распределены ("
                        + (occupied + draftOffice) + " из " + s.getOfficeCapacity() + ")");
            }
        } else if (outcome == Outcome.REMOTE) {
            checkRemoteForAdjust(runId, resultId, userId, date);
        }

        String previous = outcomeText(r.getOutcome());
        r.setOutcome(outcome);
        r.setSource(Source.MANUAL);
        r.setManualReason(reason.trim());
        r.setExplanation("Назначено вручную руководителем: " + reason.trim() + ". Автоматически было: " + previous + ".");
        r.setApplyStatus(outcome == Outcome.NONE ? ApplyStatus.SKIPPED : ApplyStatus.PENDING);
        r.setApplyMessage(outcome == Outcome.NONE ? "Назначение не требуется" : null);
        resultRepo.save(r);
        audit.log(actorId, "ALLOCATION_RESULT_ADJUSTED", "AllocationResult", resultId,
                "run=" + runId + ", user=" + userId + ", date=" + date + ", " + previous + " -> " + outcomeText(outcome)
                        + ", reason=" + reason.trim());
        return resultDto(r);
    }

    private void checkRemoteForAdjust(Long runId, Long resultId, Long userId, LocalDate date) {
        Map<PeriodType, LimitRule> active = settingsService.activeRules();
        LocalDate ws = PresenceService.weekStart(date);
        YearMonth ym = YearMonth.from(date);
        long weekUsed = presenceRepo.countByUser_IdAndTypeAndStatusAndPresenceDateBetween(
                userId, PresenceType.REMOTE, PresenceStatus.ACTIVE, ws, ws.plusDays(6));
        long monthUsed = presenceRepo.countByUser_IdAndTypeAndStatusAndPresenceDateBetween(
                userId, PresenceType.REMOTE, PresenceStatus.ACTIVE, ym.atDay(1), ym.atEndOfMonth());
        for (AllocationResult o : resultRepo.findByRun_IdAndUser_IdAndOutcomeAndSourceNot(
                runId, userId, Outcome.REMOTE, Source.EXISTING)) {
            if (o.getId().equals(resultId)) continue;
            LocalDate od = o.getResultDate();
            if (!od.isBefore(ws) && !od.isAfter(ws.plusDays(6))) weekUsed++;
            if (YearMonth.from(od).equals(ym)) monthUsed++;
        }
        LocalDate today = rules.today();
        List<String> violations = RemoteLimitPolicy.violations(active.get(PeriodType.WEEKLY), weekUsed,
                !today.isBefore(ws) && !today.isAfter(ws.plusDays(6)), active.get(PeriodType.MONTHLY), monthUsed,
                ym.equals(YearMonth.from(today)));
        if (!violations.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "REMOTE_LIMIT_EXCEEDED",
                    "Назначить удалённый день нельзя: " + String.join(". ", violations), violations);
        }
    }

    // ---------------------------------------------------------------- подтверждение / отклонение

    /**
     * Подтверждение выполняется в три шага, каждый — в своей короткой транзакции:
     *  1) черновик → APPLYING (под блокировкой строки запуска, поэтому параллельный второй запуск подтверждения
     *     не начнёт всё заново);
     *  2) каждый результат применяется отдельно (AllocationApplier): отказ по одной записи не откатывает остальные
     *     и не удерживает длинную транзакцию с множеством блокировок;
     *  3) запуск → CONFIRMED, уведомления сотрудникам, запись в аудит (один раз).
     * Если сервер упал на шаге 2, запуск остаётся APPLYING и подтверждение можно безопасно повторить: уже
     * применённые результаты повторно не обрабатываются.
     */
    public RunDetail confirm(Long actorId, Long runId) {
        tx.executeWithoutResult(status -> {
            AllocationRun r = runRepo.findByIdForUpdate(runId).orElseThrow(() -> ApiException.notFound("Запуск не найден"));
            if (r.getStatus() == RunStatus.CONFIRMED) {
                throw ApiException.conflict("ALREADY_CONFIRMED", "Распределение уже подтверждено");
            }
            if (r.getStatus() == RunStatus.DISCARDED) {
                throw ApiException.conflict("RUN_DISCARDED", "Этот черновик был отклонён");
            }
            r.setStatus(RunStatus.APPLYING);
            runRepo.save(r);
        });

        for (Long resultId : resultRepo.findIdsByRunAndApplyStatus(runId, ApplyStatus.PENDING)) {
            applier.applyOne(resultId, actorId);
        }

        tx.executeWithoutResult(status -> {
            AllocationRun r = runRepo.findByIdForUpdate(runId).orElseThrow(() -> ApiException.notFound("Запуск не найден"));
            if (r.getStatus() == RunStatus.CONFIRMED) {
                return; // параллельное подтверждение уже завершилось — второй раз не уведомляем
            }
            r.setStatus(RunStatus.CONFIRMED);
            r.setConfirmedById(actorId);
            r.setConfirmedAt(Instant.now(clock));
            runRepo.save(r);

            List<AllocationResult> results = resultRepo.findByRunWithUser(runId);
            Counts c = new Counts();
            results.forEach(x -> c.add(x.getOutcome(), x.getApplyStatus(), 1));
            audit.log(actorId, "ALLOCATION_CONFIRMED", "AllocationRun", runId,
                    "applied=" + c.applied + ", skipped=" + c.skipped + ", failed=" + c.failed);
            notifyEmployees(r, results);
        });
        return tx.execute(status -> get(runId));
    }

    @Transactional
    public RunDto discard(Long actorId, Long runId) {
        AllocationRun r = runRepo.findByIdForUpdate(runId).orElseThrow(() -> ApiException.notFound("Запуск не найден"));
        if (r.getStatus() != RunStatus.DRAFT) {
            throw ApiException.conflict("RUN_NOT_EDITABLE", "Отклонить можно только черновик распределения");
        }
        r.setStatus(RunStatus.DISCARDED);
        runRepo.save(r);
        audit.log(actorId, "ALLOCATION_DISCARDED", "AllocationRun", runId, "range=" + r.getRangeFrom() + ".." + r.getRangeTo());
        Counts c = new Counts();
        resultRepo.findByRunWithUser(runId).forEach(x -> c.add(x.getOutcome(), x.getApplyStatus(), 1));
        return runDto(r, names(Set.of(r.getCreatedById())), c);
    }

    private void notifyEmployees(AllocationRun run, List<AllocationResult> results) {
        Map<Long, List<AllocationResult>> byUser = new LinkedHashMap<>();
        for (AllocationResult r : results) {
            if (r.getSource() != Source.EXISTING) {
                byUser.computeIfAbsent(r.getUser().getId(), k -> new ArrayList<>()).add(r);
            }
        }
        String title = "Распределение на " + run.getRangeFrom().format(DM) + "–" + run.getRangeTo().format(DMY) + " подтверждено";
        byUser.forEach((userId, list) -> {
            List<String> lines = new ArrayList<>();
            for (AllocationResult r : list) {
                String text;
                if (r.getOutcome() == Outcome.NONE) {
                    text = "без назначения (предпочтение не удовлетворено)";
                } else if (r.getApplyStatus() == ApplyStatus.APPLIED) {
                    text = outcomeText(r.getOutcome());
                } else {
                    text = "не применено" + (r.getApplyMessage() == null ? "" : " (" + r.getApplyMessage() + ")");
                }
                lines.add(r.getResultDate().format(DM) + ": " + text);
            }
            notifications.notify(userId, "ALLOCATION_CONFIRMED", title, String.join("; ", lines));
        });
    }

    // ---------------------------------------------------------------- экспорт

    @Transactional(readOnly = true)
    public byte[] csv(Long runId) {
        AllocationRun run = runRepo.findById(runId).orElseThrow(() -> ApiException.notFound("Запуск не найден"));
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Дата;Сотрудник;Предпочтение;Результат;Источник;Применение;Объяснение\n");
        for (AllocationResult r : resultRepo.findByRunWithUser(run.getId())) {
            sb.append(r.getResultDate()).append(';')
                    .append(cell(r.getUser().fullName())).append(';')
                    .append(prefText(r.getPreference())).append(';')
                    .append(outcomeText(r.getOutcome())).append(';')
                    .append(r.getSource() == Source.AUTO ? "алгоритм" : r.getSource() == Source.MANUAL ? "вручную" : "уже была запись").append(';')
                    .append(r.getApplyStatus()).append(';')
                    .append(cell(r.getExplanation())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Экранирование ячейки CSV; значения, начинающиеся с «=+-@», защищены от выполнения как формулы в Excel. */
    static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---------------------------------------------------------------- вспомогательное

    private static void validatePeriod(LocalDate from, LocalDate to) {
        PeriodValidator.validate(from, to);
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("INVALID_PERIOD", "Период распределения не может превышать " + MAX_DAYS + " дней");
        }
    }

    private static String outcomeText(Outcome o) {
        return switch (o) {
            case OFFICE -> "офис";
            case REMOTE -> "удалённо";
            case NONE -> "без назначения";
        };
    }

    private static String prefText(PreferenceType p) {
        return switch (p) {
            case OFFICE -> "офис";
            case REMOTE -> "удалённо";
            case FLEXIBLE -> "гибко";
        };
    }

    private Map<Long, String> names(Set<Long> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::fullName));
    }

    private static final class Counts {
        long total, office, remote, none, applied, skipped, failed, pending;

        void add(Outcome o, ApplyStatus a, long n) {
            total += n;
            if (o == Outcome.OFFICE) office += n;
            else if (o == Outcome.REMOTE) remote += n;
            else none += n;
            switch (a) {
                case APPLIED -> applied += n;
                case SKIPPED -> skipped += n;
                case FAILED -> failed += n;
                case PENDING -> pending += n;
            }
        }
    }

    private RunDto runDto(AllocationRun r, Map<Long, String> names, Counts c) {
        return new RunDto(r.getId(), r.getRangeFrom(), r.getRangeTo(), r.getStatus(), r.getPolicy(), r.getCapacity(),
                r.getCreatedById(), names.get(r.getCreatedById()), r.getCreatedAt(),
                r.getConfirmedById() == null ? null : names.get(r.getConfirmedById()), r.getConfirmedAt(),
                c.total, c.office, c.remote, c.none, c.applied, c.skipped, c.failed, c.pending);
    }

    private ResultDto resultDto(AllocationResult r) {
        return new ResultDto(r.getId(), r.getUser().getId(), r.getUser().fullName(), r.getResultDate(), r.getPreference(),
                r.getOutcome(), r.getSource(), r.getExplanation(), r.getManualReason(), r.getApplyStatus(),
                r.getApplyMessage());
    }

    private RunDetail detail(AllocationRun run, List<AllocationResult> results) {
        Counts c = new Counts();
        Map<LocalDate, long[]> perDay = new TreeMap<>(); // [wanted office, office, remote, none]
        for (AllocationResult r : results) {
            c.add(r.getOutcome(), r.getApplyStatus(), 1);
            long[] d = perDay.computeIfAbsent(r.getResultDate(), k -> new long[4]);
            if (r.getPreference() == PreferenceType.OFFICE) d[0]++;
            if (r.getOutcome() == Outcome.OFFICE) d[1]++;
            else if (r.getOutcome() == Outcome.REMOTE) d[2]++;
            else d[3]++;
        }
        List<DaySummary> days = perDay.entrySet().stream()
                .map(e -> new DaySummary(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
        Set<Long> ids = new HashSet<>();
        ids.add(run.getCreatedById());
        if (run.getConfirmedById() != null) ids.add(run.getConfirmedById());
        return new RunDetail(runDto(run, names(ids), c), days, results.stream().map(this::resultDto).toList());
    }
}
