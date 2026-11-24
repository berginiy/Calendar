package com.example.officebooking.auth;

import com.example.officebooking.auth.AuthDtos.LoginResponse;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.security.JwtService;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserDto;
import com.example.officebooking.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Хеш для выравнивания времени ответа при неизвестном email (генерируется при старте). */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
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
}
