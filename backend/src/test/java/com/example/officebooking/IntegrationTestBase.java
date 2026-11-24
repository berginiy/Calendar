package com.example.officebooking;

import com.example.officebooking.user.Role;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Базовый класс интеграционных тестов: настоящий PostgreSQL в Testcontainers (нужен Docker). */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {
    public static final String PASSWORD = "Passw0rd!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;

    @BeforeEach
    void resetState() {
        jdbc.update("delete from audit_log");
        jdbc.update("delete from presence_records");
        jdbc.update("update app_settings set office_capacity = 10, allow_same_day_booking = false, "
                + "cancellation_deadline_hours = 0, updated_by_user_id = null, "
                + "working_days = 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY'");
        jdbc.update("update limit_rules set max_days = 1, enabled = true, updated_by_user_id = null where period_type = 'WEEKLY'");
        jdbc.update("update limit_rules set max_days = 4, enabled = true, updated_by_user_id = null where period_type = 'MONTHLY'");
        jdbc.update("delete from users where email like 'it-%'");
    }

    protected User newUser(String name, Role role) {
        User u = new User();
        u.setFirstName(name);
        u.setLastName("Tester");
        u.setEmail("it-" + name.toLowerCase() + "@example.com");
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRole(role);
        u.setEnabled(true);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        return users.save(u);
    }

    protected static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Bishkek"));
    }

    /** Ближайший понедельник не раньше чем через две недели. */
    protected static LocalDate futureMonday() {
        return today().plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Первые n рабочих дней (пн–пт) месяца, начинающегося через два месяца. */
    protected static List<LocalDate> firstWeekdaysOfFutureMonth(int n) {
        LocalDate d = today().plusMonths(2).withDayOfMonth(1);
        List<LocalDate> result = new ArrayList<>();
        while (result.size() < n) {
            if (d.getDayOfWeek().getValue() <= 5) result.add(d);
            d = d.plusDays(1);
        }
        return result;
    }
}
