package com.example.officebooking.security;

import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Проверяет Bearer-токен и при каждом запросе перечитывает пользователя из БД,
 * чтобы отключённый пользователь или смена роли действовали немедленно.
 *
 * Пока у пользователя установлен флаг must_change_password (временный пароль), разрешены только
 * запросы /api/auth/me и /api/auth/change-password — остальные получают 403 PASSWORD_CHANGE_REQUIRED.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = jwtService.parseUserId(header.substring(7)).flatMap(users::findById)
                    .filter(User::isEnabled).orElse(null);
            if (user != null) {
                if (user.isMustChangePassword() && !allowedWhilePasswordChangeRequired(request)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\","
                            + "\"message\":\"Необходимо сменить временный пароль\",\"details\":[],"
                            + "\"timestamp\":\"" + Instant.now() + "\"}");
                    return;
                }
                UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getRole());
                var auth = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean allowedWhilePasswordChangeRequired(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/me") || path.equals("/api/auth/change-password");
    }
}
