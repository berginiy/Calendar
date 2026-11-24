package com.example.officebooking.analytics;

import com.example.officebooking.analytics.AnalyticsCalculator.DayStat;
import com.example.officebooking.analytics.AnalyticsCalculator.Result;
import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.presence.PresenceRepository;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.SettingsService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private static final Locale RU = Locale.forLanguageTag("ru");

    private final PresenceRepository repo;
    private final SettingsService settingsService;

    public AnalyticsService(PresenceRepository repo, SettingsService settingsService) {
        this.repo = repo;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public Result analytics(LocalDate from, LocalDate to) {
        PeriodValidator.validate(from, to);
        AppSettings s = settingsService.current();
        Map<LocalDate, Long> office = counts(PresenceType.OFFICE, from, to);
        Map<LocalDate, Long> remote = counts(PresenceType.REMOTE, from, to);
        long cancelled = repo.countByStatusAndPresenceDateBetween(PresenceStatus.CANCELLED, from, to);
        return AnalyticsCalculator.compute(from, to, s.getOfficeCapacity(), s.workingDaysSet(), office, remote, cancelled);
    }

    /** CSV по дням (разделитель «;», UTF-8 с BOM — корректно открывается в Excel). Персональных данных нет. */
    @Transactional(readOnly = true)
    public byte[] csv(LocalDate from, LocalDate to) {
        Result r = analytics(from, to);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Дата;День недели;Рабочий день;Мест всего;Забронировано в офисе;Удалённо;Свободно;Загрузка, %\n");
        for (DayStat d : r.daily()) {
            sb.append(d.date()).append(';')
                    .append(d.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, RU)).append(';')
                    .append(d.workingDay() ? "да" : "нет").append(';')
                    .append(r.capacity()).append(';')
                    .append(d.office()).append(';')
                    .append(d.remote()).append(';')
                    .append(d.free()).append(';')
                    .append(String.valueOf(d.utilization()).replace('.', ',')).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<LocalDate, Long> counts(PresenceType type, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : repo.countByDate(type, PresenceStatus.ACTIVE, from, to)) {
            map.put((LocalDate) row[0], (Long) row[1]);
        }
        return map;
    }
}
