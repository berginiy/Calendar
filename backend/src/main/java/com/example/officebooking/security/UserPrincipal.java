package com.example.officebooking.security;

import com.example.officebooking.user.Role;

/** Текущий пользователь из SecurityContext. Идентификатор берётся только отсюда, не из запроса. */
public record UserPrincipal(Long id, String email, Role role) {
    public boolean isManager() {
        return role == Role.MANAGER;
    }
}
