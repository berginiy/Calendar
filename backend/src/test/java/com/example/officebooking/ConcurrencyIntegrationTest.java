package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.officebooking.common.ApiException;
import com.example.officebooking.presence.PresenceService;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.user.Role;
import com.example.officebooking.user.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Проверка защиты от гонок на настоящем PostgreSQL. */
class ConcurrencyIntegrationTest extends IntegrationTestBase {
    @Autowired PresenceService service;

    /** Запускает все задачи одновременно; возвращает {успехи, бизнес-конфликты}. */
    private int[] runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    task.call();
                    ok.incrementAndGet();
                } catch (ApiException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(); // пробросит неожиданные исключения (deadlock и т.п.)
        pool.shutdown();
        return new int[]{ok.get(), conflicts.get()};
    }

    @Test
    void neverMoreThanCapacityOfficeBookingsOnOneDate() throws Exception {
        LocalDate date = futureMonday();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            User u = newUser("Race" + i, Role.EMPLOYEE);
            tasks.add(() -> service.create(u.getId(), date, PresenceType.OFFICE));
        }
        int[] r = runConcurrently(tasks);

        assertThat(r[0]).isEqualTo(10);
        assertThat(r[1]).isEqualTo(5);
        Integer active = jdbc.queryForObject("select count(*) from presence_records "
                + "where presence_date = ? and type = 'OFFICE' and status = 'ACTIVE'", Integer.class, date);
        assertThat(active).isEqualTo(10);
    }

    @Test
    void sameUserCannotCreateTwoRecordsForOneDateConcurrently() throws Exception {
        LocalDate date = futureMonday();
        User u = newUser("Double", Role.EMPLOYEE);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            PresenceType type = i % 2 == 0 ? PresenceType.OFFICE : PresenceType.REMOTE;
            tasks.add(() -> service.create(u.getId(), date, type));
        }
        int[] r = runConcurrently(tasks);

        assertThat(r[0]).isEqualTo(1);
        Integer active = jdbc.queryForObject("select count(*) from presence_records "
                + "where user_id = ? and presence_date = ? and status = 'ACTIVE'", Integer.class, u.getId(), date);
        assertThat(active).isEqualTo(1);
    }

    @Test
    void weeklyRemoteLimitCannotBeExceededConcurrently() throws Exception {
        LocalDate monday = futureMonday();
        User u = newUser("WeekRace", Role.EMPLOYEE);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate d = monday.plusDays(i);
            tasks.add(() -> service.create(u.getId(), d, PresenceType.REMOTE));
        }
        int[] r = runConcurrently(tasks);

        assertThat(r[0]).isEqualTo(1);
        assertThat(r[1]).isEqualTo(4);
    }

    @Test
    void monthlyRemoteLimitCannotBeExceededConcurrently() throws Exception {
        jdbc.update("update limit_rules set enabled = false where period_type = 'WEEKLY'");
        User u = newUser("MonthRace", Role.EMPLOYEE);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (LocalDate d : firstWeekdaysOfFutureMonth(8)) {
            tasks.add(() -> service.create(u.getId(), d, PresenceType.REMOTE));
        }
        int[] r = runConcurrently(tasks);

        assertThat(r[0]).isEqualTo(4);
        assertThat(r[1]).isEqualTo(4);
    }
}
