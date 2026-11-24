package com.example.officebooking.auth;

import com.example.officebooking.user.UserDto;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank(message = "Введите email") String email,
                               @NotBlank(message = "Введите пароль") String password) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserDto user) {}
}
