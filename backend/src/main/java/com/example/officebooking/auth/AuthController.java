package com.example.officebooking.auth;

import com.example.officebooking.auth.AuthDtos.ChangePasswordRequest;
import com.example.officebooking.auth.AuthDtos.LoginRequest;
import com.example.officebooking.auth.AuthDtos.LoginResponse;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.security.UserPrincipal;
import com.example.officebooking.user.UserDto;
import com.example.officebooking.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserPrincipal me) {
        return users.findById(me.id()).map(UserDto::from)
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal UserPrincipal me,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(me.id(), request.currentPassword(), request.newPassword());
    }
}
