package com.example.officebooking.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Чистый расчёт аналитики по готовым счётчикам (без доступа к БД — удобно тестировать).
 * Все показатели строятся по ПЛАНИРУЕМЫМ бронированиям (активные записи), а не по фактическому присутствию.
 */
public final class AnalyticsCalculator {
    private AnalyticsCalculator() {}

    public record DayStat(LocalDate date, boolean workingDay, long office, long remote, long free, double utilization) {}

    public record WeekdayStat(DayOfWeek day, int days, double avgOccupied, long maxOccupied, double avgUtilization) {}

    public record PeriodStat(String period, LocalDate from, LocalDate to, int workingDays, long office, long remote,
                             double utilization) {}

    public record Result(String basis, LocalDate from, LocalDate to, int capacity, int workingDays,
                         long officeBookings, long remoteDays, long cancelled, double overallUtilization,
                         List<WeekdayStat> byWeekday, List<DayStat> topDemand, List<DayStat> lowDemand,
                         List<PeriodStat> weekly, List<PeriodStat> monthly, List<DayStat> daily) {}

    public static Result compute(LocalDate from, LocalDate to, int capacity, Set<DayOfWeek> working,
                                 Map<LocalDate, Long> office, Map<LocalDate, Long> remote, long cancelled) {
        List<DayStat> daily = new ArrayList<>();
        long officeTotal = 0;
        long remoteTotal = 0;
        long officeOnWorking = 0;
        int workingCount = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            boolean w = working.contains(d.getDayOfWeek());
            long o = office.getOrDefault(d, 0L);
            long r = remote.getOrDefault(d, 0L);
            officeTotal += o;
            remoteTotal += r;
            if (w) {
                workingCount++;
                officeOnWorking += o;
            }
            daily.add(new DayStat(d, w, o, r, Math.max(0, capacity - o), pct(o, capacity)));
        }

        List<DayStat> workingDays = daily.stream().filter(DayStat::workingDay).toList();
        double overall = pct(officeOnWorking, (long) capacity * workingCount);

        List<WeekdayStat> byWeekday = new ArrayList<>();
        for (DayOfWeek dow : DayOfWeek.values()) {
            List<DayStat> same = workingDays.stream().filter(s -> s.date().getDayOfWeek() == dow).toList();
            if (same.isEmpty()) continue;
            long sum = same.stream().mapToLong(DayStat::office).sum();
            long max = same.stream().mapToLong(DayStat::office).max().orElse(0);
            byWeekday.add(new WeekdayStat(dow, same.size(), round1((double) sum / same.size()), max,
                    pct(sum, (long) capacity * same.size())));
        }

        List<DayStat> top = workingDays.stream()
                .sorted(Comparator.comparingLong(DayStat::office).reversed().thenComparing(DayStat::date))
                .limit(5).toList();
        List<DayStat> low = workingDays.stream()
                .sorted(Comparator.comparingLong(DayStat::office).thenComparing(DayStat::date))
                .limit(5).toList();

        Map<LocalDate, List<DayStat>> byWeek = new LinkedHashMap<>();
        Map<YearMonth, List<DayStat>> byMonth = new LinkedHashMap<>();
        for (DayStat s : daily) {
            byWeek.computeIfAbsent(s.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    k -> new ArrayList<>()).add(s);
            byMonth.computeIfAbsent(YearMonth.from(s.date()), k -> new ArrayList<>()).add(s);
        }
        List<PeriodStat> weekly = new ArrayList<>();
        byWeek.forEach((start, list) -> weekly.add(aggregate(start.toString(), list, capacity)));
        List<PeriodStat> monthly = new ArrayList<>();
        byMonth.forEach((ym, list) -> monthly.add(aggregate(ym.toString(), list, capacity)));

        return new Result("PLANNED", from, to, capacity, workingCount, officeTotal, remoteTotal, cancelled, overall,
                byWeekday, top, low, weekly, monthly, daily);
    }

    private static PeriodStat aggregate(String key, List<DayStat> days, int capacity) {
        long office = days.stream().mapToLong(DayStat::office).sum();
        long remote = days.stream().mapToLong(DayStat::remote).sum();
        List<DayStat> working = days.stream().filter(DayStat::workingDay).toList();
        long officeOnWorking = working.stream().mapToLong(DayStat::office).sum();
        return new PeriodStat(key, days.get(0).date(), days.get(days.size() - 1).date(), working.size(), office, remote,
                pct(officeOnWorking, (long) capacity * working.size()));
    }

    static double pct(long part, long whole) {
        return whole <= 0 ? 0 : round1(part * 100.0 / whole);
    }

    static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
