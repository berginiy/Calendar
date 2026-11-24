package com.example.officebooking.statistics;

import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.presence.BookingRules;
import com.example.officebooking.presence.PresenceDtos.PresenceDto;
import com.example.officebooking.presence.PresenceRepository;
import com.example.officebooking.presence.PresenceService;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.settings.LimitRule;
import com.example.officebooking.settings.PeriodType;
import com.example.officebooking.settings.SettingsService;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {

    /** max == null и unlimited == true — лимит отключён («Без ограничений»). */
    public record LimitUsage(LocalDate periodFrom, LocalDate periodTo, long used, Integer max, boolean unlimited,
                             Long remaining) {}

    public record MyStats(LocalDate from, LocalDate to, LimitUsage week, LimitUsage month,
                          List<PresenceDto> remoteDays) {}

    private final PresenceRepository repo;
    private final SettingsService settingsService;
    private final BookingRules rules;

    public StatisticsService(PresenceRepository repo, SettingsService settingsService, BookingRules rules) {
        this.repo = repo;
        this.settingsService = settingsService;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public MyStats myStats(Long userId, LocalDate from, LocalDate to) {
        LocalDate today = rules.today();
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today.with(TemporalAdjusters.lastDayOfMonth());
        PeriodValidator.validate(f, t);

        Map<PeriodType, LimitRule> active = settingsService.activeRules();
        LocalDate wFrom = PresenceService.weekStart(today);
        LocalDate wTo = wFrom.plusDays(6);
        LocalDate mFrom = today.withDayOfMonth(1);
        LocalDate mTo = today.with(TemporalAdjusters.lastDayOfMonth());

        LimitUsage week = usage(userId, wFrom, wTo, active.get(PeriodType.WEEKLY));
        LimitUsage month = usage(userId, mFrom, mTo, active.get(PeriodType.MONTHLY));
        List<PresenceDto> history = repo
                .findByUser_IdAndTypeAndPresenceDateBetweenOrderByPresenceDateDescIdDesc(userId, PresenceType.REMOTE, f, t)
                .stream().map(PresenceDto::from).toList();
        return new MyStats(f, t, week, month, history);
    }

    private LimitUsage usage(Long userId, LocalDate from, LocalDate to, LimitRule rule) {
        long used = repo.countByUser_IdAndTypeAndStatusAndPresenceDateBetween(
                userId, PresenceType.REMOTE, PresenceStatus.ACTIVE, from, to);
        if (rule == null) {
            return new LimitUsage(from, to, used, null, true, null);
        }
        return new LimitUsage(from, to, used, rule.getMaxDays(), false, Math.max(0, rule.getMaxDays() - used));
    }
}
