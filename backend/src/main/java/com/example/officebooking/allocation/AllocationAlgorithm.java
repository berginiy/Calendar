package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationTypes.Outcome;
import com.example.officebooking.allocation.AllocationTypes.Policy;
import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.allocation.AllocationTypes.Source;
import com.example.officebooking.presence.PresenceType;
import com.example.officebooking.presence.RemoteLimitPolicy;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Простой и объяснимый алгоритм распределения. Чистая функция: не обращается к БД, результат детерминирован
 * (одни и те же входные данные всегда дают один и тот же результат).
 *
 * Порядок работы, дни обрабатываются по возрастанию даты:
 *  1. Сотрудники, у которых на дату уже есть активная запись, НЕ перераспределяются (source = EXISTING).
 *  2. Желающие в офис получают свободные места по приоритету политики; не получившие остаются без назначения
 *     (NONE) с объяснением — удалёнка им автоматически не навязывается.
 *  3. Желающие удалёнку получают её, если не превышены недельный и месячный лимиты; иначе — NONE с объяснением.
 *  4. «Гибкие» получают оставшиеся места, а если мест нет — удалённый день (при свободном лимите), иначе NONE.
 *
 * Приоритет при ROTATION (по убыванию важности): больше ранее неудовлетворённых офисных предпочтений
 * (история + отказы в этом запуске) → меньше мест, уже выданных в этом запуске → более раняя первая подача →
 * идентификатор сотрудника (стабильный tie-breaker). Чувствительные персональные данные не используются.
 */
public final class AllocationAlgorithm {
    private AllocationAlgorithm() {}

    public record Candidate(Long userId, PreferenceType preference, Instant firstSubmittedAt) {}

    /**
     * @param capacity   вместимость офиса на день
     * @param freeSeats  свободных мест на день с учётом уже существующих активных бронирований
     * @param existing   сотрудники, у которых на эту дату уже есть активная запись, и её тип
     * @param candidates сотрудники с активным предпочтением на эту дату
     */
    public record Day(LocalDate date, int capacity, int freeSeats, Map<Long, PresenceType> existing,
                      List<Candidate> candidates) {}

    /**
     * @param weeklyMax / monthlyMax действующие лимиты удалёнки (null — лимит отключён)
     * @param missedHistory          userId → число ранее неудовлетворённых офисных предпочтений (из подтверждённых запусков)
     * @param initialWeekRemote      userId → (понедельник недели → уже занято удалённых дней)
     * @param initialMonthRemote     userId → (месяц → уже занято удалённых дней)
     */
    public record Params(Policy policy, Integer weeklyMax, Integer monthlyMax, LocalDate today,
                         Map<Long, Integer> missedHistory,
                         Map<Long, Map<LocalDate, Integer>> initialWeekRemote,
                         Map<Long, Map<YearMonth, Integer>> initialMonthRemote) {}

    public record Decision(Long userId, LocalDate date, PreferenceType preference, Outcome outcome, Source source,
                           String explanation) {}

    public static List<Decision> allocate(List<Day> days, Params p) {
        RemoteState remote = new RemoteState(p);
        Map<Long, Integer> missedInRun = new HashMap<>();
        Map<Long, Integer> grantedInRun = new HashMap<>();
        List<Decision> out = new ArrayList<>();

        List<Day> ordered = days.stream().sorted(Comparator.comparing(Day::date)).toList();
        for (Day day : ordered) {
            int free = day.freeSeats();
            List<Candidate> active = new ArrayList<>();
            for (Candidate c : day.candidates()) {
                PresenceType existing = day.existing().get(c.userId());
                if (existing != null) {
                    out.add(new Decision(c.userId(), day.date(), c.preference(),
                            existing == PresenceType.OFFICE ? Outcome.OFFICE : Outcome.REMOTE, Source.EXISTING,
                            "Запись уже существует (" + (existing == PresenceType.OFFICE ? "в офисе" : "удалённо")
                                    + ") — распределение её не меняет."));
                } else {
                    active.add(c);
                }
            }

            Comparator<Candidate> priority = comparator(p, missedInRun, grantedInRun);
            List<Candidate> office = active.stream().filter(c -> c.preference() == PreferenceType.OFFICE)
                    .sorted(priority).toList();
            List<Candidate> remoteWanters = active.stream().filter(c -> c.preference() == PreferenceType.REMOTE)
                    .sorted(Comparator.comparing(Candidate::userId)).toList();

            // 1) желающие в офис
            for (Candidate c : office) {
                if (free > 0) {
                    free--;
                    grantedInRun.merge(c.userId(), 1, Integer::sum);
                    int score = score(c, p, missedInRun);
                    String note = p.policy() == Policy.ROTATION && score > 0
                            ? " (приоритет по ротации: ранее не удовлетворено офисных предпочтений — " + score + ")" : "";
                    out.add(new Decision(c.userId(), day.date(), c.preference(), Outcome.OFFICE, Source.AUTO,
                            "Офисное предпочтение учтено: место выделено" + note + "."));
                } else {
                    missedInRun.merge(c.userId(), 1, Integer::sum);
                    out.add(new Decision(c.userId(), day.date(), c.preference(), Outcome.NONE, Source.AUTO,
                            noSeatsText(day, p.policy())));
                }
            }

            // 2) желающие удалёнку
            for (Candidate c : remoteWanters) {
                List<String> violations = remote.violations(c.userId(), day.date());
                if (violations.isEmpty()) {
                    remote.add(c.userId(), day.date());
                    out.add(new Decision(c.userId(), day.date(), c.preference(), Outcome.REMOTE, Source.AUTO,
                            "Предпочтение «удалённо» учтено: удалённый день назначен."));
                } else {
                    out.add(new Decision(c.userId(), day.date(), c.preference(), Outcome.NONE, Source.AUTO,
                            "Удалённый день не назначен: " + String.join(". ", violations) + "."));
                }
            }

            // 3) гибкие — остаток мест, затем удалёнка
            List<Candidate> flexible = active.stream().filter(c -> c.preference() == PreferenceType.FLEXIBLE)
                    .sorted(priority).toList();
            for (Candidate c : flexible) {
                if (free > 0) {
                    free--;
                    grantedInRun.merge(c.userId(), 1, Integer::sum);
                    out.add(new Decision(c.userId(), day.date(), c.preference(), Outcome.OFFICE, Source.AUTO,
                            "Гибкое предпочтение: назначено свободное офисное место."));
                    continue;
                }
                List<String> violations = remote.violations(c.userId(), day.date());
                if (violations.isEmpty()) {
                    remote.add(c.userId(), day.date());
                    out.add(new Decision(c.userId(), day.date(), c.preference(), Outcome.REMOTE, Source.AUTO,
                            "Гибкое предпочтение: свободных офисных мест не осталось, назначен удалённый день."));
                } else {
                    out.add(new Decision(c.userId(), day.date(), c.preference(), Outcome.NONE, Source.AUTO,
                            "Гибкое предпочтение: свободных офисных мест не осталось, а удалённый день назначить нельзя. "
                                    + String.join(". ", violations) + "."));
                }
            }
        }
        out.sort(Comparator.comparing(Decision::date).thenComparing(Decision::userId));
        return out;
    }

    private static String noSeatsText(Day day, Policy policy) {
        if (day.freeSeats() <= 0) {
            return "Офисное предпочтение не удовлетворено: на эту дату свободных мест нет.";
        }
        String why = policy == Policy.ROTATION
                ? "Приоритет получили сотрудники с большим количеством ранее неудовлетворённых офисных предпочтений."
                : "Приоритет получили сотрудники, подавшие предпочтение раньше.";
        return "Офисное предпочтение не удовлетворено: свободные места (" + day.freeSeats()
                + ") на эту дату распределены. " + why;
    }

    private static int score(Candidate c, Params p, Map<Long, Integer> missedInRun) {
        return p.missedHistory().getOrDefault(c.userId(), 0) + missedInRun.getOrDefault(c.userId(), 0);
    }

    private static Comparator<Candidate> comparator(Params p, Map<Long, Integer> missedInRun,
                                                    Map<Long, Integer> grantedInRun) {
        Comparator<Candidate> byFirstSubmission = Comparator.comparing(Candidate::firstSubmittedAt)
                .thenComparing(Candidate::userId);
        if (p.policy() == Policy.SUBMISSION_ORDER) {
            return byFirstSubmission;
        }
        return Comparator.<Candidate>comparingInt(c -> -score(c, p, missedInRun))
                .thenComparingInt(c -> grantedInRun.getOrDefault(c.userId(), 0))
                .thenComparing(byFirstSubmission);
    }

    /** Учёт удалённых дней по сотрудникам, недельным и месячным окнам (начальное состояние + назначенное в запуске). */
    private static final class RemoteState {
        private final Integer weeklyMax;
        private final Integer monthlyMax;
        private final LocalDate today;
        private final Map<Long, Map<LocalDate, Integer>> week = new HashMap<>();
        private final Map<Long, Map<YearMonth, Integer>> month = new HashMap<>();

        RemoteState(Params p) {
            this.weeklyMax = p.weeklyMax();
            this.monthlyMax = p.monthlyMax();
            this.today = p.today();
            p.initialWeekRemote().forEach((u, m) -> week.put(u, new HashMap<>(m)));
            p.initialMonthRemote().forEach((u, m) -> month.put(u, new HashMap<>(m)));
        }

        private static LocalDate weekStart(LocalDate d) {
            return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        List<String> violations(Long userId, LocalDate date) {
            LocalDate ws = weekStart(date);
            YearMonth ym = YearMonth.from(date);
            int w = week.getOrDefault(userId, Map.of()).getOrDefault(ws, 0);
            int m = month.getOrDefault(userId, Map.of()).getOrDefault(ym, 0);
            boolean weekCurrent = !today.isBefore(ws) && !today.isAfter(ws.plusDays(6));
            boolean monthCurrent = ym.equals(YearMonth.from(today));
            return RemoteLimitPolicy.violationsForLimits(weeklyMax, w, weekCurrent, monthlyMax, m, monthCurrent);
        }

        void add(Long userId, LocalDate date) {
            week.computeIfAbsent(userId, k -> new HashMap<>()).merge(weekStart(date), 1, Integer::sum);
            month.computeIfAbsent(userId, k -> new HashMap<>()).merge(YearMonth.from(date), 1, Integer::sum);
        }
    }
}
