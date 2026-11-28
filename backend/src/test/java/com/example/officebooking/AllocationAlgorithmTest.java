package com.example.officebooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.officebooking.allocation.AllocationAlgorithm;
import com.example.officebooking.allocation.AllocationAlgorithm.Candidate;
import com.example.officebooking.allocation.AllocationAlgorithm.Day;
import com.example.officebooking.allocation.AllocationAlgorithm.Decision;
import com.example.officebooking.allocation.AllocationAlgorithm.Params;
import com.example.officebooking.allocation.AllocationTypes.Outcome;
import com.example.officebooking.allocation.AllocationTypes.Policy;
import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.allocation.AllocationTypes.Source;
import com.example.officebooking.presence.PresenceType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit-тесты алгоритма распределения (без БД и Spring). Дата «сегодня» зафиксирована. */
class AllocationAlgorithmTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);   // понедельник
    private static final LocalDate MON = LocalDate.of(2026, 11, 30);     // понедельник следующей недели
    private static final LocalDate TUE = MON.plusDays(1);
    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00Z");

    private static Candidate cand(long id, PreferenceType pref, int submittedMinutes) {
        return new Candidate(id, pref, T0.plusSeconds(submittedMinutes * 60L));
    }

    private static Day day(LocalDate date, int capacity, int free, Candidate... candidates) {
        return new Day(date, capacity, free, new HashMap<>(), new ArrayList<>(List.of(candidates)));
    }

    private static Params params(Policy policy, Integer weekly, Integer monthly, Map<Long, Integer> history) {
        return new Params(policy, weekly, monthly, TODAY, history, new HashMap<>(), new HashMap<>());
    }

    private static Decision find(List<Decision> ds, long userId, LocalDate date) {
        return ds.stream().filter(d -> d.userId() == userId && d.date().equals(date)).findFirst().orElseThrow();
    }

    @Test
    void everyoneGetsOfficeWhenSeatsAreEnough() {
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(MON, 5, 5,
                cand(1, PreferenceType.OFFICE, 0), cand(2, PreferenceType.OFFICE, 1), cand(3, PreferenceType.OFFICE, 2))),
                params(Policy.ROTATION, null, null, Map.of()));
        assertEquals(3, r.size());
        assertTrue(r.stream().allMatch(d -> d.outcome() == Outcome.OFFICE));
    }

    @Test
    void neverAssignsMoreOfficeSeatsThanFree() {
        List<Candidate> cs = new ArrayList<>();
        for (int i = 1; i <= 15; i++) cs.add(cand(i, PreferenceType.OFFICE, i));
        List<Decision> r = AllocationAlgorithm.allocate(
                List.of(new Day(MON, 10, 10, new HashMap<>(), cs)), params(Policy.ROTATION, null, null, Map.of()));
        assertEquals(10, r.stream().filter(d -> d.outcome() == Outcome.OFFICE).count());
        assertEquals(5, r.stream().filter(d -> d.outcome() == Outcome.NONE).count());
    }

    @Test
    void rotationGivesTheSeatToTheMostOftenDeniedEmployee() {
        Map<Long, Integer> history = Map.of(1L, 0, 2L, 1, 3L, 2);
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(MON, 1, 1,
                cand(1, PreferenceType.OFFICE, 0), cand(2, PreferenceType.OFFICE, 1), cand(3, PreferenceType.OFFICE, 99))),
                params(Policy.ROTATION, null, null, history));
        assertEquals(Outcome.OFFICE, find(r, 3, MON).outcome());
        assertEquals(Outcome.NONE, find(r, 1, MON).outcome());
        assertEquals(Outcome.NONE, find(r, 2, MON).outcome());
        assertTrue(find(r, 1, MON).explanation().contains("не удовлетворено"));
        assertTrue(find(r, 1, MON).explanation().contains("ранее неудовлетворённых"));
    }

    @Test
    void deniedEmployeeWinsTheNextDayOfTheSameRun() {
        // 1 место в день; у обоих нет истории; сотрудник 1 подал раньше → в первый день получает он,
        // во второй день приоритет у сотрудника 2, которому отказали накануне
        List<Decision> r = AllocationAlgorithm.allocate(List.of(
                day(MON, 1, 1, cand(1, PreferenceType.OFFICE, 0), cand(2, PreferenceType.OFFICE, 5)),
                day(TUE, 1, 1, cand(1, PreferenceType.OFFICE, 0), cand(2, PreferenceType.OFFICE, 5))),
                params(Policy.ROTATION, null, null, Map.of()));
        assertEquals(Outcome.OFFICE, find(r, 1, MON).outcome());
        assertEquals(Outcome.NONE, find(r, 2, MON).outcome());
        assertEquals(Outcome.OFFICE, find(r, 2, TUE).outcome());
        assertEquals(Outcome.NONE, find(r, 1, TUE).outcome());
    }

    @Test
    void submissionOrderUsesFirstSubmissionAndIgnoresUserId() {
        // сотрудник 5 подал раньше сотрудника 1 → получает место, несмотря на больший id
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(MON, 1, 1,
                cand(1, PreferenceType.OFFICE, 50), cand(5, PreferenceType.OFFICE, 10))),
                params(Policy.SUBMISSION_ORDER, null, null, Map.of(1L, 9)));
        assertEquals(Outcome.OFFICE, find(r, 5, MON).outcome());
        assertEquals(Outcome.NONE, find(r, 1, MON).outcome());
        assertTrue(find(r, 1, MON).explanation().contains("подавшие предпочтение раньше"));
    }

    @Test
    void existingRecordsAreNeverChangedAndDoNotCompeteForSeats() {
        Map<Long, PresenceType> existing = new HashMap<>();
        existing.put(1L, PresenceType.OFFICE);
        Day d = new Day(MON, 2, 1, existing, new ArrayList<>(List.of(
                cand(1, PreferenceType.REMOTE, 0), cand(2, PreferenceType.OFFICE, 1), cand(3, PreferenceType.OFFICE, 2))));
        List<Decision> r = AllocationAlgorithm.allocate(List.of(d), params(Policy.ROTATION, null, null, Map.of()));
        Decision fixed = find(r, 1, MON);
        assertEquals(Source.EXISTING, fixed.source());
        assertEquals(Outcome.OFFICE, fixed.outcome());
        assertEquals(1, r.stream().filter(x -> x.source() == Source.AUTO && x.outcome() == Outcome.OFFICE).count());
    }

    @Test
    void remoteWeeklyLimitIsRespectedAcrossDays() {
        List<Decision> r = AllocationAlgorithm.allocate(List.of(
                day(MON, 5, 5, cand(1, PreferenceType.REMOTE, 0)),
                day(TUE, 5, 5, cand(1, PreferenceType.REMOTE, 0))),
                params(Policy.ROTATION, 1, null, Map.of()));
        assertEquals(Outcome.REMOTE, find(r, 1, MON).outcome());
        assertEquals(Outcome.NONE, find(r, 1, TUE).outcome());
        assertTrue(find(r, 1, TUE).explanation().contains("лимит удалённой работы"));
    }

    @Test
    void alreadyUsedRemoteQuotaBlocksAssignment() {
        Map<Long, Map<LocalDate, Integer>> week = new HashMap<>();
        week.put(1L, new HashMap<>(Map.of(MON, 1)));
        Params p = new Params(Policy.ROTATION, 1, null, TODAY, Map.of(), week, new HashMap<>());
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(TUE, 5, 5, cand(1, PreferenceType.REMOTE, 0))), p);
        assertEquals(Outcome.NONE, find(r, 1, TUE).outcome());
    }

    @Test
    void remoteMonthlyLimitIsRespected() {
        Map<Long, Map<YearMonth, Integer>> month = new HashMap<>();
        month.put(1L, new HashMap<>(Map.of(YearMonth.from(MON), 4)));
        Params p = new Params(Policy.ROTATION, null, 4, TODAY, Map.of(), new HashMap<>(), month);
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(MON, 5, 5, cand(1, PreferenceType.REMOTE, 0))), p);
        assertEquals(Outcome.NONE, find(r, 1, MON).outcome());
        assertTrue(find(r, 1, MON).explanation().contains("месяц"));
    }

    @Test
    void zeroRemoteLimitForbidsAnyRemoteAssignment() {
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(MON, 5, 5, cand(1, PreferenceType.REMOTE, 0))),
                params(Policy.ROTATION, 0, null, Map.of()));
        assertEquals(Outcome.NONE, find(r, 1, MON).outcome());
    }

    @Test
    void officeWantersHavePriorityOverFlexibleForSeats() {
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(MON, 1, 1,
                cand(1, PreferenceType.FLEXIBLE, 0), cand(2, PreferenceType.OFFICE, 99))),
                params(Policy.ROTATION, null, null, Map.of()));
        assertEquals(Outcome.OFFICE, find(r, 2, MON).outcome());
        assertEquals(Outcome.REMOTE, find(r, 1, MON).outcome());
    }

    @Test
    void flexibleGetsLeftoverSeatsOtherwiseRemoteOtherwiseNone() {
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(MON, 1, 1,
                cand(1, PreferenceType.FLEXIBLE, 0), cand(2, PreferenceType.FLEXIBLE, 1), cand(3, PreferenceType.FLEXIBLE, 2))),
                params(Policy.ROTATION, 1, null, Map.of()));
        // 1 место + 1 удалённый день на каждого по лимиту → все трое получают назначение, место только у одного
        assertEquals(1, r.stream().filter(d -> d.outcome() == Outcome.OFFICE).count());
        assertEquals(2, r.stream().filter(d -> d.outcome() == Outcome.REMOTE).count());

        List<Decision> r2 = AllocationAlgorithm.allocate(List.of(day(MON, 1, 0,
                cand(1, PreferenceType.FLEXIBLE, 0))), params(Policy.ROTATION, 0, null, Map.of()));
        assertEquals(Outcome.NONE, find(r2, 1, MON).outcome());
    }

    @Test
    void noFreeSeatsIsExplained() {
        List<Decision> r = AllocationAlgorithm.allocate(List.of(day(MON, 10, 0, cand(1, PreferenceType.OFFICE, 0))),
                params(Policy.ROTATION, null, null, Map.of()));
        assertEquals(Outcome.NONE, find(r, 1, MON).outcome());
        assertTrue(find(r, 1, MON).explanation().contains("свободных мест нет"));
    }

    @Test
    void resultDoesNotDependOnInputOrder() {
        List<Candidate> cs = new ArrayList<>();
        for (int i = 1; i <= 8; i++) cs.add(cand(i, i % 3 == 0 ? PreferenceType.FLEXIBLE : PreferenceType.OFFICE, i % 4));
        Map<Long, Integer> history = Map.of(2L, 3, 5L, 1);
        List<Decision> a = AllocationAlgorithm.allocate(List.of(new Day(MON, 3, 3, new HashMap<>(), new ArrayList<>(cs))),
                params(Policy.ROTATION, 1, 4, history));
        List<Candidate> shuffled = new ArrayList<>(cs);
        Collections.reverse(shuffled);
        List<Decision> b = AllocationAlgorithm.allocate(List.of(new Day(MON, 3, 3, new HashMap<>(), shuffled)),
                params(Policy.ROTATION, 1, 4, history));
        assertEquals(a, b);
    }

    @Test
    void repeatedRunWithSameInputGivesSameResult() {
        Day d = day(MON, 2, 2, cand(1, PreferenceType.OFFICE, 0), cand(2, PreferenceType.OFFICE, 1), cand(3, PreferenceType.OFFICE, 2));
        Params p = params(Policy.ROTATION, 1, 4, Map.of(3L, 1));
        assertEquals(AllocationAlgorithm.allocate(List.of(d), p), AllocationAlgorithm.allocate(List.of(d), p));
    }
}
