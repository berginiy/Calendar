package com.example.officebooking.auth;

import com.example.officebooking.audit.AuditService;
import com.example.officebooking.auth.AuthDtos.LoginResponse;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.security.JwtService;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserDto;
import com.example.officebooking.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final Clock clock;
    /** Хеш для выравнивания времени ответа при неизвестном email (генерируется при старте). */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit,
                       Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    public LoginResponse login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        boolean ok = encoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok || !user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Неверный email или пароль");
        }
        return new LoginResponse(jwt.generate(user), "Bearer", jwt.expiresInSeconds(), UserDto.from(user));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Текущий пароль указан неверно");
        }
        if (currentPassword.equals(newPassword)) {
            throw ApiException.badRequest("SAME_PASSWORD", "Новый пароль должен отличаться от текущего");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setUpdatedAt(Instant.now(clock));
        audit.log(userId, "PASSWORD_CHANGED", "User", userId, null);
    }
}
