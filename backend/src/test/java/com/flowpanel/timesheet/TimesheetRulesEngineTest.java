package com.flowpanel.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimesheetRulesEngineTest {

    static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);
    static final BigDecimal SEVEN = new BigDecimal("7.00");

    static List<BigDecimal> hours(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    static TimesheetRulesEngine.Week week(List<BigDecimal> days, boolean overtime) {
        return new TimesheetRulesEngine.Week(MONDAY, days, new BigDecimal("35.00"), SEVEN, overtime);
    }

    @Test
    void contractedWeekHasNoAnomaly() {
        assertThat(TimesheetRulesEngine.check(week(hours("7", "7", "7", "7", "7", "0", "0"), false))).isEmpty();
    }

    @Test
    void hoursAboveContractWithoutOvertimeAreFlaggedWithTheDays() {
        var anomalies = TimesheetRulesEngine.check(week(TimesheetService.SEEDED_WEEK, false));
        assertThat(anomalies).hasSize(1);
        var a = anomalies.get(0);
        assertThat(a.ruleId()).isEqualTo(TimesheetRulesEngine.CONTRACTED_HOURS);
        assertThat(a.message()).isEqualTo("41 h worked vs 35 h contracted, no overtime agreed");
        assertThat(a.flaggedDays()).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void overtimeAllowedAcceptsExtraHoursButNotTheLegalMaximums() {
        assertThat(TimesheetRulesEngine.check(week(TimesheetService.SEEDED_WEEK, true))).isEmpty();
        var anomalies = TimesheetRulesEngine.check(week(hours("10", "10", "10", "10", "9", "0", "0"), true));
        assertThat(anomalies).extracting(TimesheetRulesEngine.Anomaly::ruleId).containsExactly(TimesheetRulesEngine.WEEKLY_MAXIMUM);
    }

    @Test
    void dailyLimitIsCheckedPerDay() {
        var anomalies = TimesheetRulesEngine.check(week(hours("7", "7", "11", "5", "5", "0", "0"), true));
        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).ruleId()).isEqualTo(TimesheetRulesEngine.DAILY_LIMIT);
        assertThat(anomalies.get(0).message()).isEqualTo("Wednesday: 11 h exceeds the 10 h daily limit");
        assertThat(anomalies.get(0).flaggedDays()).containsExactly(2);
    }

    @Test
    void contractedPatternIsProRatedForPartialWeeks() {
        var pattern = TimesheetRulesEngine.contractedPattern(MONDAY, LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 30), SEVEN);
        assertThat(TimesheetRulesEngine.contractedFor(pattern)).isEqualByComparingTo("21");
        assertThat(pattern.get(0)).isEqualByComparingTo("0");
        assertThat(pattern.get(5)).isEqualByComparingTo("0");
    }

    @Test
    void approvedHoursWhenOvertimeIsApprovedKeepTheSubmittedTotal() {
        assertThat(TimesheetRulesEngine.approvedHours(TimesheetService.SEEDED_WEEK, null, false)).isEqualByComparingTo("41.00");
    }

    @Test
    void approvedHoursWhenReturnedToSupplierUseTheCorrectedSheet() {
        var corrected = TimesheetRulesEngine.contractedPattern(MONDAY, MONDAY, MONDAY.plusDays(30), SEVEN);
        assertThat(TimesheetRulesEngine.approvedHours(TimesheetService.SEEDED_WEEK, corrected, true)).isEqualByComparingTo("35.00");
    }

    @Test
    void scheduledDailyIsWeeklyOverFiveDays() {
        assertThat(TimesheetRulesEngine.scheduledDaily(new BigDecimal("35"))).isEqualByComparingTo("7.00");
        assertThat(TimesheetRulesEngine.scheduledDaily(new BigDecimal("32.5"))).isEqualByComparingTo("6.50");
    }
}
