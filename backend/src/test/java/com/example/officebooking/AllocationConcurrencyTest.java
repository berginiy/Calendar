package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.officebooking.allocation.AllocationDtos.RunDetail;
import com.example.officebooking.allocation.AllocationService;
import com.example.officebooking.allocation.AllocationTypes.Policy;
import com.example.officebooking.allocation.PreferenceService;
import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.presence.PresenceService;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.user.Role;
import com.example.officebooking.user.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Параллельное подтверждение и состязание распределения с обычными бронированиями (на настоящем PostgreSQL). */
class AllocationConcurrencyTest extends IntegrationTestBase {
    @Autowired AllocationService allocation;
    @Autowired PreferenceService preferences;

    private long managerId() {
        return jdbc.queryForObject("select id from users where email = 'manager@test.local'", Long.class);
    }

    private void enable() {
        jdbc.update("update app_settings set allocation_enabled = true, allocation_policy = ?, preference_deadline_hours = 24",
                Policy.ROTATION.name());
    }

    @Test
    void parallelConfirmationsApplyEachResultOnceAndNotifyOnce() throws Exception {
        enable();
        jdbc.update("update app_settings set office_capacity = 3");
        LocalDate d = futureMonday();
        List<User> us = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User u = newUser("Par" + i, Role.EMPLOYEE);
            us.add(u);
            preferences.create(u.getId(), d, PreferenceType.OFFICE);
        }
        long runId = allocation.run(managerId(), d, d).run().id();

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                try {
                    RunDetail r = allocation.confirm(managerId(), runId);
                    assertThat(r.run().status().name()).isEqualTo("CONFIRMED");
                } catch (ApiException e) {
                    assertThat(e.getCode()).isEqualTo("ALREADY_CONFIRMED"); // допустимый исход для опоздавших
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();

        assertThat(jdbc.queryForObject("select count(*) from presence_records where presence_date = ? and type = 'OFFICE' and status = 'ACTIVE'",
                Integer.class, d)).isEqualTo(3); // ровно по вместимости, без дублей
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ALLOCATION_CONFIRMED'", Integer.class)).isEqualTo(1);
        for (User u : us) {
            assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'ALLOCATION_CONFIRMED'",
                    Integer.class, u.getId())).isEqualTo(1);
        }
    }

    @Test
    void manualBookingsRacingWithConfirmationNeverExceedCapacity() throws Exception {
        enable();
        jdbc.update("update app_settings set office_capacity = 3");
        LocalDate d = futureMonday();
        for (int i = 0; i < 3; i++) {
            User u = newUser("Pw" + i, Role.EMPLOYEE);
            preferences.create(u.getId(), d, PreferenceType.OFFICE);
        }
        long runId = allocation.run(managerId(), d, d).run().id();
        List<User> walkIns = new ArrayList<>();
        for (int i = 0; i < 3; i++) walkIns.add(newUser("Wi" + i, Role.EMPLOYEE));

        // подтверждение распределения и обычные бронирования идут одновременно и борются за те же 3 места
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        fs.add(pool.submit(() -> {
            start.await();
            allocation.confirm(managerId(), runId);
            return null;
        }));
        for (User u : walkIns) {
            fs.add(pool.submit(() -> {
                start.await();
                try {
                    presenceService.create(u.getId(), d, PresenceType.OFFICE);
                } catch (ApiException ignored) {
                    // «мест нет» — допустимый исход
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : fs) f.get(); // неожиданные исключения (например, взаимная блокировка) проваливают тест
        pool.shutdown();

        assertThat(jdbc.queryForObject("select count(*) from presence_records where presence_date = ? and type = 'OFFICE' and status = 'ACTIVE'",
                Integer.class, d)).isLessThanOrEqualTo(3);
    }

    @Autowired PresenceService presenceService;
}
