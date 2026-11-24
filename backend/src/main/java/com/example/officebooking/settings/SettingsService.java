package com.example.officebooking.settings;

import com.example.officebooking.audit.AuditService;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.presence.PresenceRepository;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.settings.SettingsDtos.LimitRuleDto;
import com.example.officebooking.settings.SettingsDtos.SettingsRequest;
import com.example.officebooking.settings.SettingsDtos.SettingsResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
    private final AppSettingsRepository settingsRepo;
    private final LimitRuleRepository ruleRepo;
    private final PresenceRepository presenceRepo;
    private final AuditService audit;
    private final Clock clock;

    public SettingsService(AppSettingsRepository settingsRepo, LimitRuleRepository ruleRepo,
                           PresenceRepository presenceRepo, AuditService audit, Clock clock) {
        this.settingsRepo = settingsRepo;
        this.ruleRepo = ruleRepo;
        this.presenceRepo = presenceRepo;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AppSettings current() {
        return settingsRepo.findById(AppSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Настройки приложения не инициализированы"));
    }

    /** Используется при создании записей: берёт разделяемую блокировку строки настроек до конца транзакции. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AppSettings lockedForBooking() {
        return settingsRepo.findSharedLocked()
                .orElseThrow(() -> new IllegalStateException("Настройки приложения не инициализированы"));
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return SettingsResponse.from(current());
    }

    @Transactional
    public SettingsResponse update(Long actorId, SettingsRequest req) {
        AppSettings s = settingsRepo.findExclusiveLocked()
                .orElseThrow(() -> new IllegalStateException("Настройки приложения не инициализированы"));

        if (req.officeCapacity() < s.getOfficeCapacity()) {
            // уменьшать можно только если ни на одну будущую дату уже нет большего числа броней
            LocalDate today = LocalDate.now(clock);
            List<Object[]> over = presenceRepo.findDatesOverCapacity(
                    PresenceType.OFFICE, PresenceStatus.ACTIVE, today, req.officeCapacity());
            if (!over.isEmpty()) {
                Object[] first = over.get(0);
                throw ApiException.conflict("CAPACITY_CONFLICT",
                        "Нельзя установить " + req.officeCapacity() + " мест: на " + first[0] + " уже оформлено "
                                + first[1] + " бронирований. Сначала отмените лишние бронирования ("
                                + over.size() + " дат с конфликтом)");
            }
        }

        String before = "capacity=" + s.getOfficeCapacity() + ", sameDay=" + s.isAllowSameDayBooking()
                + ", cancelHours=" + s.getCancellationDeadlineHours() + ", workingDays=" + s.getWorkingDays();
        s.setOfficeCapacity(req.officeCapacity());
        s.setAllowSameDayBooking(req.allowSameDayBooking());
        s.setCancellationDeadlineHours(req.cancellationDeadlineHours());
        s.setWorkingDaysSet(req.workingDays());
        s.setUpdatedAt(Instant.now(clock));
        s.setUpdatedByUserId(actorId);
        settingsRepo.save(s);

        String after = "capacity=" + s.getOfficeCapacity() + ", sameDay=" + s.isAllowSameDayBooking()
                + ", cancelHours=" + s.getCancellationDeadlineHours() + ", workingDays=" + s.getWorkingDays();
        if (before.split(",")[0].equals(after.split(",")[0])) {
            audit.log(actorId, "SETTINGS_CHANGED", "AppSettings", s.getId(), "before: " + before + " | after: " + after);
        } else {
            audit.log(actorId, "CAPACITY_CHANGED", "AppSettings", s.getId(), "before: " + before + " | after: " + after);
        }
        return SettingsResponse.from(s);
    }

    @Transactional(readOnly = true)
    public List<LimitRuleDto> getRules() {
        return ruleRepo.findAll().stream()
                .sorted((a, b) -> a.getPeriodType().compareTo(b.getPeriodType()))
                .map(LimitRuleDto::from).toList();
    }

    /** Активные (enabled) правила по типу периода. */
    @Transactional(readOnly = true)
    public Map<PeriodType, LimitRule> activeRules() {
        Map<PeriodType, LimitRule> map = new EnumMap<>(PeriodType.class);
        for (LimitRule r : ruleRepo.findAll()) {
            if (r.isEnabled()) {
                map.put(r.getPeriodType(), r);
            }
        }
        return map;
    }

    @Transactional
    public List<LimitRuleDto> updateRules(Long actorId, List<LimitRuleDto> requested) {
        Set<PeriodType> seen = new HashSet<>();
        for (LimitRuleDto dto : requested) {
            if (!seen.add(dto.periodType())) {
                throw ApiException.badRequest("VALIDATION_ERROR",
                        "Правило для периода " + dto.periodType() + " указано несколько раз");
            }
        }
        for (LimitRuleDto dto : requested) {
            LimitRule rule = ruleRepo.findByPeriodType(dto.periodType()).orElseGet(() -> {
                LimitRule r = new LimitRule();
                r.setPeriodType(dto.periodType());
                return r;
            });
            String before = rule.getId() == null ? "-" : "max=" + rule.getMaxDays() + ", enabled=" + rule.isEnabled();
            rule.setMaxDays(dto.maxDays());
            rule.setEnabled(dto.enabled());
            rule.setUpdatedAt(Instant.now(clock));
            rule.setUpdatedByUserId(actorId);
            ruleRepo.save(rule);
            audit.log(actorId, "LIMIT_RULE_CHANGED", "LimitRule", rule.getPeriodType(),
                    "before: " + before + " | after: max=" + dto.maxDays() + ", enabled=" + dto.enabled());
        }
        return getRules();
    }
}
