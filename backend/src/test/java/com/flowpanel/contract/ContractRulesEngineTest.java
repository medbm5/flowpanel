package com.flowpanel.contract;

import static com.flowpanel.contract.ContractRulesEngine.CERTIFICATE_ATTACHED;
import static com.flowpanel.contract.ContractRulesEngine.DATES_WITHIN_ORDER;
import static com.flowpanel.contract.ContractRulesEngine.LEGAL_REASON_PRESENT;
import static com.flowpanel.contract.ContractRulesEngine.RATE_MATCHES_ORDER;
import static org.assertj.core.api.Assertions.assertThat;

import com.flowpanel.contract.ContractRulesEngine.RuleResult;
import com.flowpanel.intake.Order;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractRulesEngineTest {

    static final LocalDate START = LocalDate.of(2026, 10, 5);
    static final LocalDate END = LocalDate.of(2026, 10, 16);
    static final Order ORDER = new Order("Cariste", 2, "Entrepôt Lille Lesquin", START, END, "Lun–ven 06:00–13:00",
            new BigDecimal("35"), new BigDecimal("13.20"), "ACTIVITY_INCREASE", "", List.of("CACES R489 cat. 3"), false);
    static final List<String> HOLDS = List.of("CACES R489 cat. 3", "SST");

    static ContractTerms valid() {
        return new ContractTerms("Cariste", START, END, new BigDecimal("13.20"), new BigDecimal("35"), false,
                "ACTIVITY_INCREASE", "", List.of("CACES R489 cat. 3"));
    }

    static RuleResult rule(ContractTerms terms, String id) {
        return ContractRulesEngine.check(terms, ORDER, HOLDS).stream().filter(r -> r.ruleId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void validContractPassesEveryRule() {
        List<RuleResult> results = ContractRulesEngine.check(valid(), ORDER, HOLDS);
        assertThat(results).hasSize(4).allMatch(RuleResult::passed);
        assertThat(ContractRulesEngine.hasBlockingIssue(results)).isFalse();
    }

    // ---- RATE_MATCHES_ORDER

    @Test
    void ratePassesWhenEqualIgnoringScale() {
        assertThat(rule(valid().withRate(new BigDecimal("13.2")), RATE_MATCHES_ORDER).passed()).isTrue();
    }

    @Test
    void rateFailsWhenDifferentAndIsFixable() {
        ContractTerms wrong = valid().withRate(new BigDecimal("12.90"));
        RuleResult r = rule(wrong, RATE_MATCHES_ORDER);
        assertThat(r.passed()).isFalse();
        assertThat(r.blocking()).isTrue();
        assertThat(r.message()).contains("12.90").contains("13.20");
        assertThat(ContractRulesEngine.fix(RATE_MATCHES_ORDER, wrong, ORDER, HOLDS).orElseThrow().hourlyRate()).isEqualByComparingTo("13.20");
    }

    // ---- LEGAL_REASON_PRESENT

    @Test
    void legalReasonPassesForAllowedReason() {
        assertThat(rule(valid(), LEGAL_REASON_PRESENT).passed()).isTrue();
    }

    @Test
    void legalReasonFailsWhenMissingOrReplacementWithoutName() {
        assertThat(rule(valid().withReason(null, null), LEGAL_REASON_PRESENT).passed()).isFalse();
        assertThat(rule(valid().withReason("REPLACEMENT", ""), LEGAL_REASON_PRESENT).message()).isEqualTo("Replaced employee is missing");
        ContractTerms fixed = ContractRulesEngine.fix(LEGAL_REASON_PRESENT, valid().withReason(null, null), ORDER, HOLDS).orElseThrow();
        assertThat(fixed.legalReason()).isEqualTo("ACTIVITY_INCREASE");
    }

    // ---- CERTIFICATE_ATTACHED

    @Test
    void certificatePassesWhenAttached() {
        assertThat(rule(valid(), CERTIFICATE_ATTACHED).passed()).isTrue();
    }

    @Test
    void certificateFailsWhenNotAttachedAndIsFixableOnlyIfHeld() {
        ContractTerms none = valid().withCertificates(List.of());
        RuleResult r = rule(none, CERTIFICATE_ATTACHED);
        assertThat(r.passed()).isFalse();
        assertThat(r.autoFixable()).isTrue();
        assertThat(ContractRulesEngine.fix(CERTIFICATE_ATTACHED, none, ORDER, HOLDS).orElseThrow().attachedCertificates())
                .containsExactly("CACES R489 cat. 3");

        RuleResult notHeld = ContractRulesEngine.check(none, ORDER, List.of("SST")).stream()
                .filter(x -> x.ruleId().equals(CERTIFICATE_ATTACHED)).findFirst().orElseThrow();
        assertThat(notHeld.autoFixable()).isFalse();
        assertThat(ContractRulesEngine.fix(CERTIFICATE_ATTACHED, none, ORDER, List.of("SST"))).isEmpty();
    }

    // ---- DATES_WITHIN_ORDER

    @Test
    void datesPassWhenInsideTheOrderPeriod() {
        assertThat(rule(valid().withDates(START.plusDays(1), END.minusDays(1)), DATES_WITHIN_ORDER).passed()).isTrue();
    }

    @Test
    void datesFailWhenEndIsAfterTheOrderEndAndFixClampsThem() {
        ContractTerms late = valid().withDates(START, END.plusDays(7));
        RuleResult r = rule(late, DATES_WITHIN_ORDER);
        assertThat(r.passed()).isFalse();
        assertThat(r.message()).contains("2026-10-23");
        ContractTerms fixed = ContractRulesEngine.fix(DATES_WITHIN_ORDER, late, ORDER, HOLDS).orElseThrow();
        assertThat(fixed.endDate()).isEqualTo(END);
        assertThat(fixed.startDate()).isEqualTo(START);
    }

    @Test
    void fixOfAPassingRuleIsEmpty() {
        assertThat(ContractRulesEngine.fix(RATE_MATCHES_ORDER, valid(), ORDER, HOLDS)).isEmpty();
    }
}
