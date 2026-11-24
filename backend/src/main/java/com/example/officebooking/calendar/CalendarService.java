package com.example.officebooking.calendar;

import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.presence.BookingRules;
import com.example.officebooking.presence.PresenceRecord;
import com.example.officebooking.presence.PresenceRepository;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.SettingsService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    public record CalendarDay(LocalDate date, boolean workingDay, int capacity, long occupied, long free,
                              String myStatus, Long myRecordId,
                              boolean canBook, boolean canRemote, boolean canCancel, String unavailableReason) {}

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

    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(Long userId, LocalDate from, LocalDate to) {
        PeriodValidator.validate(from, to);
        AppSettings settings = settingsService.current();
        Map<LocalDate, Long> counts = officeCounts(from, to);
        Map<LocalDate, PresenceRecord> mine = new HashMap<>();
        for (PresenceRecord r : repo.findByUser_IdAndStatusAndPresenceDateBetween(userId, PresenceStatus.ACTIVE, from, to)) {
            mine.put(r.getPresenceDate(), r);
        }

        List<CalendarDay> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long occupied = counts.getOrDefault(d, 0L);
            long free = Math.max(0, settings.getOfficeCapacity() - occupied);
            PresenceRecord my = mine.get(d);
            String myStatus = my == null ? "NONE" : my.getType().name();
            boolean working = settings.workingDaysSet().contains(d.getDayOfWeek());

            Optional<String> dateReason = rules.unavailableReason(d, settings.isAllowSameDayBooking(), settings.workingDaysSet());
            boolean dateOk = dateReason.isEmpty();
            boolean free2 = my == null;
            boolean canBook = dateOk && free2 && free > 0;
            boolean canRemote = dateOk && free2;
            boolean canCancel = my != null
                    && rules.cancellationBlockedReason(d, settings.getCancellationDeadlineHours()).isEmpty();

            String reason = dateReason.orElse(null);
            if (reason == null && free2 && free == 0) {
                reason = "Свободных мест нет";
            }
            result.add(new CalendarDay(d, working, settings.getOfficeCapacity(), occupied, free, myStatus,
                    my == null ? null : my.getId(), canBook, canRemote, canCancel, reason));
        }
        return result;
    }
}
