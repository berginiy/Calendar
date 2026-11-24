package com.example.officebooking.calendar;

import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.presence.BookingRules;
import com.example.officebooking.presence.PresenceRecord;
import com.example.officebooking.presence.PresenceRepository;
import com.example.officebooking.presence.PresenceService;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.presence.RemoteLimitPolicy;
import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.LimitRule;
import com.example.officebooking.settings.PeriodType;
import com.example.officebooking.settings.SettingsService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {
    static final String ALREADY_HAS_RECORD = "На эту дату у вас уже есть запись";
    static final String NO_SEATS = "Свободных мест нет";

    /**
     * Состояние дня для текущего пользователя. Все решения «можно/нельзя» принимает сервер.
     *
     * @param occupancyLevel FREE | ALMOST_FULL | FULL — планируемая загрузка по бронированиям (не факт присутствия)
     * @param unavailableReason общая причина уровня даты (прошлое, выходной, мест нет) — для подсветки ячейки
     * @param bookReason    почему нельзя забронировать место (null — можно)
     * @param remoteReason  почему нельзя отметить удалёнку (null — можно); включает превышение лимитов
     * @param cancelReason  почему нельзя отменить имеющуюся запись (null — можно или записи нет)
     */
    public record CalendarDay(LocalDate date, boolean workingDay, int capacity, long occupied, long free,
                              String occupancyLevel, String myStatus, Long myRecordId,
                              boolean canBook, boolean canRemote, boolean canCancel,
                              String unavailableReason, String bookReason, String remoteReason,
                              String cancelReason) {}

    private final PresenceRepository repo;
    private final SettingsService settingsService;
    private final BookingRules rules;

    public CalendarService(PresenceRepository repo, SettingsService settingsService, BookingRules rules) {
        this.repo = repo;
        this.settingsService = settingsService;
        this.rules = rules;
    }

    /** Счётчики занятости по датам (без персональных данных). Ключ — дата, значение — активные OFFICE. */
    @Transactional(readOnly = true)
    public Map<LocalDate, Long> officeCounts(LocalDate from, LocalDate to) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : repo.countByDate(PresenceType.OFFICE, PresenceStatus.ACTIVE, from, to)) {
            counts.put((LocalDate) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** «Почти заполнено» — осталось не более 20% мест (но не меньше одного). */
    public static String occupancyLevel(long free, int capacity) {
        if (free <= 0) return "FULL";
        long threshold = Math.max(1, (long) Math.ceil(capacity * 0.2));
        return free <= threshold ? "ALMOST_FULL" : "FREE";
    }

    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(Long userId, LocalDate from, LocalDate to) {
        PeriodValidator.validate(from, to);
        AppSettings settings = settingsService.current();
        Map<PeriodType, LimitRule> activeRules = settingsService.activeRules();
        LimitRule weekRule = activeRules.get(PeriodType.WEEKLY);
        LimitRule monthRule = activeRules.get(PeriodType.MONTHLY);
        LocalDate today = rules.today();

        Map<LocalDate, Long> counts = officeCounts(from, to);
        Map<LocalDate, PresenceRecord> mine = new HashMap<>();
        for (PresenceRecord r : repo.findByUser_IdAndStatusAndPresenceDateBetween(userId, PresenceStatus.ACTIVE, from, to)) {
            mine.put(r.getPresenceDate(), r);
        }

        // использованные удалённые дни по неделям и месяцам: берём недели/месяцы целиком, даже если они выходят за период
        LocalDate wFrom = PresenceService.weekStart(from);
        LocalDate mFrom = from.withDayOfMonth(1);
        LocalDate wTo = PresenceService.weekStart(to).plusDays(6);
        LocalDate mTo = to.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate usageFrom = wFrom.isBefore(mFrom) ? wFrom : mFrom;
        LocalDate usageTo = wTo.isAfter(mTo) ? wTo : mTo;
        Map<LocalDate, Long> weekUsed = new HashMap<>();
        Map<YearMonth, Long> monthUsed = new HashMap<>();
        for (PresenceRecord r : repo.findByUser_IdAndTypeAndStatusAndPresenceDateBetween(
                userId, PresenceType.REMOTE, PresenceStatus.ACTIVE, usageFrom, usageTo)) {
            weekUsed.merge(PresenceService.weekStart(r.getPresenceDate()), 1L, Long::sum);
            monthUsed.merge(YearMonth.from(r.getPresenceDate()), 1L, Long::sum);
        }

        List<CalendarDay> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long occupied = counts.getOrDefault(d, 0L);
            long free = Math.max(0, settings.getOfficeCapacity() - occupied);
            PresenceRecord my = mine.get(d);
            boolean working = settings.workingDaysSet().contains(d.getDayOfWeek());
            Optional<String> dateReason = rules.unavailableReason(d, settings.isAllowSameDayBooking(), settings.workingDaysSet());

            String bookReason;
            String remoteReason = null;
            if (my != null) {
                bookReason = ALREADY_HAS_RECORD;
                remoteReason = ALREADY_HAS_RECORD;
            } else if (dateReason.isPresent()) {
                bookReason = dateReason.get();
                remoteReason = dateReason.get();
            } else {
                bookReason = free == 0 ? NO_SEATS : null;
                LocalDate ws = PresenceService.weekStart(d);
                YearMonth ym = YearMonth.from(d);
                List<String> violations = RemoteLimitPolicy.violations(
                        weekRule, weekUsed.getOrDefault(ws, 0L), !today.isBefore(ws) && !today.isAfter(ws.plusDays(6)),
                        monthRule, monthUsed.getOrDefault(ym, 0L), ym.equals(YearMonth.from(today)));
                if (!violations.isEmpty()) {
                    remoteReason = String.join(". ", violations);
                }
            }

            String cancelReason = my == null ? null
                    : rules.cancellationBlockedReason(d, settings.getCancellationDeadlineHours()).orElse(null);

            String general = dateReason.orElse(null);
            if (general == null && my == null && free == 0) general = NO_SEATS;

            result.add(new CalendarDay(d, working, settings.getOfficeCapacity(), occupied, free,
                    occupancyLevel(free, settings.getOfficeCapacity()),
                    my == null ? "NONE" : my.getType().name(), my == null ? null : my.getId(),
                    bookReason == null, remoteReason == null, my != null && cancelReason == null,
                    general, bookReason, remoteReason, cancelReason));
        }
        return result;
    }
}
