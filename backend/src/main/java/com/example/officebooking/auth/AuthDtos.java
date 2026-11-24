package com.example.officebooking.auth;

import com.example.officebooking.user.UserDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank(message = "Введите email") String email,
                               @NotBlank(message = "Введите пароль") String password) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserDto user) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Введите текущий пароль") String currentPassword,
            @NotBlank(message = "Введите новый пароль")
            @Size(min = 8, max = 72, message = "Пароль должен содержать от 8 до 72 символов") String newPassword) {}
}
