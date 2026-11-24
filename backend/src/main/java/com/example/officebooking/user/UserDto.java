package com.example.officebooking.user;

/** Публичное представление пользователя. Хеш пароля никогда не отдаётся. */
public record UserDto(Long id, String firstName, String lastName, String email, Role role, boolean enabled,
                      boolean mustChangePassword) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getRole(), u.isEnabled(),
                u.isMustChangePassword());
    }
}
