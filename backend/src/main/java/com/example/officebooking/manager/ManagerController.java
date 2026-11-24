package com.example.officebooking.manager;

import com.example.officebooking.common.PageResponse;
import com.example.officebooking.manager.ManagerDtos.*;
import com.example.officebooking.presence.PresenceStatus;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.security.UserPrincipal;
import com.example.officebooking.settings.SettingsDtos.LimitRuleDto;
import com.example.officebooking.settings.SettingsDtos.SettingsRequest;
import com.example.officebooking.settings.SettingsDtos.SettingsResponse;
import com.example.officebooking.settings.SettingsService;
import com.example.officebooking.user.UserDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Все пути /api/manager/** дополнительно защищены в SecurityConfig (hasRole MANAGER). */
@RestController
@RequestMapping("/api/manager")
public class ManagerController {
    private static final String DATE = "yyyy-MM-dd";

    private final ManagerService service;
    private final SettingsService settings;

    public ManagerController(ManagerService service, SettingsService settings) {
        this.service = service;
        this.settings = settings;
    }

    @GetMapping("/users")
    public List<UserDto> users() {
        return service.users();
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate date,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String q) {
        return service.dashboard(date, status, q);
    }

    @GetMapping("/occupancy")
    public List<OccupancyDay> occupancy(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to) {
        return service.occupancy(from, to);
    }

    @GetMapping("/reports/remote-days")
    public PageResponse<ReportRow> remoteDays(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                                              @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) PresenceStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "date") String sortBy,
                                              @RequestParam(defaultValue = "desc") String direction) {
        return service.records(PresenceType.REMOTE, from, to, userId, status, page, size, sortBy, direction);
    }

    @GetMapping("/reports/bookings")
    public PageResponse<ReportRow> bookings(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) PresenceStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "date") String sortBy,
                                            @RequestParam(defaultValue = "desc") String direction) {
        return service.records(PresenceType.OFFICE, from, to, userId, status, page, size, sortBy, direction);
    }

    @GetMapping("/reports/summary")
    public PageResponse<SummaryRow> summary(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "userName") String sortBy,
                                            @RequestParam(defaultValue = "asc") String direction) {
        return service.summary(from, to, userId, page, size, sortBy, direction);
    }

    @GetMapping("/audit")
    public PageResponse<AuditDto> audit(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to,
                                        @RequestParam(required = false) Long actorUserId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.audit(from, to, actorUserId, page, size);
    }

    @GetMapping("/settings")
    public SettingsResponse getSettings() {
        return settings.get();
    }

    @PutMapping("/settings")
    public SettingsResponse updateSettings(@AuthenticationPrincipal UserPrincipal me,
                                           @Valid @RequestBody SettingsRequest request) {
        return settings.update(me.id(), request);
    }

    @GetMapping("/limit-rules")
    public List<LimitRuleDto> getRules() {
        return settings.getRules();
    }

    @PutMapping("/limit-rules")
    public List<LimitRuleDto> updateRules(@AuthenticationPrincipal UserPrincipal me,
                                          @RequestBody @Valid List<@Valid LimitRuleDto> rules) {
        return settings.updateRules(me.id(), rules);
    }
}
