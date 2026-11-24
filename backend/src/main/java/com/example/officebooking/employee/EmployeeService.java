package com.example.officebooking.employee;

import com.example.officebooking.audit.AuditService;
import com.example.officebooking.common.AdvisoryLocks;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.common.PageResponse;
import com.example.officebooking.employee.EmployeeDtos.*;
import com.example.officebooking.presence.BookingRules;
import com.example.officebooking.presence.PresenceService;
import com.example.officebooking.user.Role;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Административное управление сотрудниками. Деактивация не удаляет данные: история записей и аудит сохраняются.
 * Защита от блокировки системы: нельзя менять роль/отключать самого себя и нельзя оставить систему без активного руководителя.
 */
@Service
public class EmployeeService {
    private static final long MANAGERS_LOCK = 2L << 40;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AdvisoryLocks locks;
    private final PresenceService presenceService;
    private final BookingRules rules;
    private final Clock clock;

    public EmployeeService(UserRepository users, PasswordEncoder encoder, AuditService audit, AdvisoryLocks locks,
                           PresenceService presenceService, BookingRules rules, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.locks = locks;
        this.presenceService = presenceService;
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDto> list(String q, Role role, Boolean enabled, int page, int size) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like, '\\'),
                        cb.like(cb.lower(root.get("lastName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\')));
            }
            if (role != null) ps.add(cb.equal(root.get("role"), role));
            if (enabled != null) ps.add(cb.equal(root.get("enabled"), enabled));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by("lastName", "firstName", "id"));
        return PageResponse.of(users.findAll(spec, pageable).map(EmployeeDto::from));
    }

    @Transactional
    public CredentialsResponse create(Long actorId, CreateEmployeeRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Сотрудник с таким email уже существует");
        }
        String temporary = generatePassword();
        User u = new User();
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(temporary));
        u.setRole(req.role());
        u.setEnabled(true);
        u.setMustChangePassword(true);
        u.setCreatedAt(Instant.now(clock));
        u.setUpdatedAt(Instant.now(clock));
        try {
            users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "Сотрудник с таким email уже существует");
        }
        audit.log(actorId, "EMPLOYEE_CREATED", "User", u.getId(), "email=" + email + ", role=" + u.getRole());
        return new CredentialsResponse(EmployeeDto.from(u), temporary);
    }

    @Transactional
    public EmployeeDto update(Long actorId, Long id, UpdateEmployeeRequest req) {
        User u = find(id);
        boolean roleChanges = u.getRole() != req.role();
        if (roleChanges) {
            if (id.equals(actorId)) {
                throw ApiException.conflict("SELF_ROLE_CHANGE", "Нельзя изменить собственную роль");
            }
            if (u.getRole() == Role.MANAGER && u.isEnabled()) {
                ensureAnotherActiveManager();
            }
        }
        String before = u.getFirstName() + " " + u.getLastName() + ", role=" + u.getRole();
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setRole(req.role());
        u.setUpdatedAt(Instant.now(clock));
        audit.log(actorId, roleChanges ? "ROLE_CHANGED" : "EMPLOYEE_UPDATED", "User", id,
                "before: " + before + " | after: " + u.getFirstName() + " " + u.getLastName() + ", role=" + u.getRole());
        return EmployeeDto.from(u);
    }

    @Transactional
    public EmployeeDto deactivate(Long actorId, Long id, boolean cancelFutureRecords) {
        if (id.equals(actorId)) {
            throw ApiException.conflict("SELF_DEACTIVATION", "Нельзя отключить собственную учётную запись");
        }
        User u = find(id);
        if (!u.isEnabled()) {
            return EmployeeDto.from(u); // идемпотентно
        }
        if (u.getRole() == Role.MANAGER) {
            ensureAnotherActiveManager();
        }
        // та же блокировка пользователя, что и при создании записей: исключает гонку «отключили — и тут же записался»
        presenceService.lockUser(id);
        int cancelled = cancelFutureRecords ? presenceService.cancelFutureForUser(id, actorId, rules.today()) : 0;
        u.setEnabled(false);
        u.setUpdatedAt(Instant.now(clock));
        audit.log(actorId, "EMPLOYEE_DEACTIVATED", "User", id, "cancelledFutureRecords=" + cancelled);
        return EmployeeDto.from(u);
    }

    @Transactional
    public EmployeeDto activate(Long actorId, Long id) {
        User u = find(id);
        if (u.isEnabled()) {
            return EmployeeDto.from(u);
        }
        u.setEnabled(true);
        u.setUpdatedAt(Instant.now(clock));
        audit.log(actorId, "EMPLOYEE_ACTIVATED", "User", id, null);
        return EmployeeDto.from(u);
    }

    /** Генерирует временный пароль и требует сменить его при первом входе. Пароль в аудит не пишется. */
    @Transactional
    public PasswordResetResponse resetPassword(Long actorId, Long id) {
        if (id.equals(actorId)) {
            throw ApiException.conflict("SELF_RESET", "Свой пароль можно изменить в профиле");
        }
        User u = find(id);
        String temporary = generatePassword();
        u.setPasswordHash(encoder.encode(temporary));
        u.setMustChangePassword(true);
        u.setUpdatedAt(Instant.now(clock));
        audit.log(actorId, "PASSWORD_RESET", "User", id, null);
        return new PasswordResetResponse(temporary);
    }

    private void ensureAnotherActiveManager() {
        locks.lock(MANAGERS_LOCK); // два руководителя не смогут одновременно отключить друг друга
        if (users.countByRoleAndEnabledTrue(Role.MANAGER) <= 1) {
            throw ApiException.conflict("LAST_MANAGER", "Нельзя оставить систему без активного руководителя");
        }
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Сотрудник не найден"));
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
