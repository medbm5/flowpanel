package com.flowpanel.timesheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic timesheet checks and approved-hours computation. Pure Java, BigDecimal hours. */
public final class TimesheetRulesEngine {

    public static final String CONTRACTED_HOURS = "CONTRACTED_HOURS";
    public static final String WEEKLY_MAXIMUM = "WEEKLY_MAXIMUM";
    public static final String DAILY_LIMIT = "DAILY_LIMIT";

    public static final BigDecimal MAX_DAILY = new BigDecimal("10");
    public static final BigDecimal MAX_WEEKLY = new BigDecimal("48");

    private TimesheetRulesEngine() {
    }

    /**
     * @param dailyHours     7 values, Monday..Sunday
     * @param contracted     hours contracted for this week (pro-rated for partial weeks)
     * @param scheduledDaily contracted hours per scheduled day
     */
    public record Week(LocalDate weekStart, List<BigDecimal> dailyHours, BigDecimal contracted, BigDecimal scheduledDaily,
                       boolean overtimeAllowed) {

        public BigDecimal total() {
            return dailyHours.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record Anomaly(String ruleId, String message, BigDecimal expected, BigDecimal actual, List<Integer> flaggedDays) {
    }

    public static List<Anomaly> check(Week week) {
        List<Anomaly> anomalies = new ArrayList<>();
        BigDecimal total = week.total();
        if (!week.overtimeAllowed() && total.compareTo(week.contracted()) > 0) {
            List<Integer> days = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                if (week.dailyHours().get(i).compareTo(week.scheduledDaily()) > 0) {
                    days.add(i);
                }
            }
            anomalies.add(new Anomaly(CONTRACTED_HOURS, fmt(total) + " h worked vs " + fmt(week.contracted())
                    + " h contracted, no overtime agreed", week.contracted(), total, days));
        }
        if (total.compareTo(MAX_WEEKLY) > 0) {
            anomalies.add(new Anomaly(WEEKLY_MAXIMUM, fmt(total) + " h exceeds the legal weekly maximum of " + fmt(MAX_WEEKLY)
                    + " h", MAX_WEEKLY, total, List.of()));
        }
        for (int i = 0; i < 7; i++) {
            BigDecimal h = week.dailyHours().get(i);
            if (h.compareTo(MAX_DAILY) > 0) {
                anomalies.add(new Anomaly(DAILY_LIMIT, dayName(i) + ": " + fmt(h) + " h exceeds the " + fmt(MAX_DAILY)
                        + " h daily limit", MAX_DAILY, h, List.of(i)));
            }
        }
        return anomalies;
    }

    /** Hours a supplier would submit for a correct week: the contracted schedule on the contracted days. */
    public static List<BigDecimal> contractedPattern(LocalDate weekStart, LocalDate contractStart, LocalDate contractEnd,
                                                     BigDecimal scheduledDaily) {
        List<BigDecimal> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            boolean workday = d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
            boolean inContract = !d.isBefore(contractStart) && !d.isAfter(contractEnd);
            days.add(workday && inContract ? scheduledDaily : BigDecimal.ZERO.setScale(2));
        }
        return days;
    }

    /** Contracted hours for the part of the week covered by the contract. */
    public static BigDecimal contractedFor(List<BigDecimal> pattern) {
        return pattern.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal scheduledDaily(BigDecimal weeklyHours) {
        return weeklyHours.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
    }

    /**
     * Approved hours of a week once its anomalies are handled: the submitted total when there is no anomaly or the
     * overtime was approved; after a return to the supplier, the corrected sheet's total.
     */
    public static BigDecimal approvedHours(List<BigDecimal> submitted, List<BigDecimal> corrected, boolean returnedToSupplier) {
        List<BigDecimal> source = returnedToSupplier && corrected != null ? corrected : submitted;
        return source.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    public static String dayName(int index) {
        return DayOfWeek.of(index + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
