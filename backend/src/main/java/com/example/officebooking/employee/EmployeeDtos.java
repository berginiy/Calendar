package com.example.officebooking.employee;

import com.example.officebooking.user.Role;
import com.example.officebooking.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class EmployeeDtos {
    private EmployeeDtos() {}

    /** Хеш пароля сюда никогда не попадает. */
    public record EmployeeDto(Long id, String firstName, String lastName, String email, Role role, boolean enabled,
                              boolean mustChangePassword, Instant createdAt) {
        public static EmployeeDto from(User u) {
            return new EmployeeDto(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getRole(),
                    u.isEnabled(), u.isMustChangePassword(), u.getCreatedAt());
        }
    }

    public record CreateEmployeeRequest(
            @NotBlank(message = "Укажите имя") @Size(max = 100, message = "Имя слишком длинное") String firstName,
            @NotBlank(message = "Укажите фамилию") @Size(max = 100, message = "Фамилия слишком длинная") String lastName,
            @NotBlank(message = "Укажите email") @Email(message = "Некорректный email")
            @Size(max = 255, message = "Email слишком длинный") String email,
            @NotNull(message = "Укажите роль") Role role) {}

    public record UpdateEmployeeRequest(
            @NotBlank(message = "Укажите имя") @Size(max = 100, message = "Имя слишком длинное") String firstName,
            @NotBlank(message = "Укажите фамилию") @Size(max = 100, message = "Фамилия слишком длинная") String lastName,
            @NotNull(message = "Укажите роль") Role role) {}

    /** По умолчанию (поле не передано) будущие записи сотрудника отменяются, чтобы освободить места. */
    public record DeactivateRequest(Boolean cancelFutureRecords) {}

    /** Временный пароль показывается менеджеру один раз и нигде не сохраняется в открытом виде. */
    public record CredentialsResponse(EmployeeDto employee, String temporaryPassword) {}

    public record PasswordResetResponse(String temporaryPassword) {}
}
