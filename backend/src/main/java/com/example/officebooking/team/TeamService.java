package com.example.officebooking.team;

import com.example.officebooking.common.ApiException;
import com.example.officebooking.presence.PresenceRecord;
import com.example.officebooking.presence.PresenceRepository;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.security.UserPrincipal;
import com.example.officebooking.settings.AppSettings;
import com.example.officebooking.settings.SettingsService;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Командный календарь. Права доступа проверяются здесь, на сервере:
 *  - руководитель всегда видит имена и статусы;
 *  - сотрудник видит имена и статусы только если руководитель включил настройку teamVisibleToEmployees,
 *    иначе — только счётчики (как требует основное ТЗ: «для общей доступности достаточно счётчиков»).
 * Email и прочие данные не передаются никому, только имя и статус.
 */
@Service
public class TeamService {
    public static final int MAX_DAYS = 31;

    public record TeamMember(Long userId, String fullName, String status) {}

    public record TeamDay(LocalDate date, boolean workingDay, long office, long remote, long notSelected,
                          List<TeamMember> members) {}

    /** visibility: FULL — имена и статусы; COUNTS_ONLY — только счётчики. */
    public record TeamResponse(String visibility, long totalEmployees, List<TeamDay> days) {}

    private final PresenceRepository presenceRepo;
    private final UserRepository userRepo;
    private final SettingsService settingsService;

    public TeamService(PresenceRepository presenceRepo, UserRepository userRepo, SettingsService settingsService) {
        this.presenceRepo = presenceRepo;
        this.userRepo = userRepo;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public TeamResponse team(UserPrincipal viewer, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_PERIOD", "Укажите корректный период");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("INVALID_PERIOD", "Командный календарь показывает не более " + MAX_DAYS + " дней");
        }
        AppSettings settings = settingsService.current();
        boolean full = viewer.isManager() || settings.isTeamVisibleToEmployees();

        List<User> enabled = userRepo.findAllByEnabledTrueOrderByLastNameAscFirstNameAsc();
        Map<LocalDate, Map<Long, PresenceType>> byDate = new HashMap<>();
        for (PresenceRecord r : presenceRepo.findAllByStatusInRangeWithUser(PresenceStatus.ACTIVE, from, to)) {
            if (r.getUser().isEnabled()) {
                byDate.computeIfAbsent(r.getPresenceDate(), k -> new HashMap<>()).put(r.getUser().getId(), r.getType());
            }
        }

        List<TeamDay> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<Long, PresenceType> statuses = byDate.getOrDefault(d, Map.of());
            long office = statuses.values().stream().filter(t -> t == PresenceType.OFFICE).count();
            long remote = statuses.values().stream().filter(t -> t == PresenceType.REMOTE).count();
            List<TeamMember> members = new ArrayList<>();
            if (full) {
                for (User u : enabled) {
                    PresenceType t = statuses.get(u.getId());
                    members.add(new TeamMember(u.getId(), u.fullName(), t == null ? "NONE" : t.name()));
                }
            }
            days.add(new TeamDay(d, settings.workingDaysSet().contains(d.getDayOfWeek()), office, remote,
                    enabled.size() - office - remote, members));
        }
        return new TeamResponse(full ? "FULL" : "COUNTS_ONLY", enabled.size(), days);
    }
}
