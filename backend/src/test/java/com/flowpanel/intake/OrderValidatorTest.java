package com.flowpanel.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderValidatorTest {

    static Map<String, String> valid() {
        Map<String, String> v = new HashMap<>();
        v.put("position", "Cariste");
        v.put("quantity", "2");
        v.put("site", "Entrepôt Lille Lesquin");
        v.put("startDate", "2026-10-05");
        v.put("endDate", "2026-10-16");
        v.put("schedule", "Lun–ven 06:00–13:00");
        v.put("weeklyHours", "35");
        v.put("hourlyRate", "13.20");
        v.put("legalReason", "ACTIVITY_INCREASE");
        v.put("replacedEmployee", "");
        v.put("requiredCertifications", "CACES R489 cat. 3");
        v.put("overtimeAllowed", "false");
        return v;
    }

    @Test
    void validOrderHasNoErrors() {
        assertThat(OrderValidator.validate(valid()).values()).allMatch(java.util.List::isEmpty);
    }

    @Test
    void endBeforeStartIsRejected() {
        var v = valid();
        v.put("endDate", "2026-10-01");
        assertThat(OrderValidator.validate(v).get("endDate")).contains("must be on or after the start date");
    }

    @Test
    void quantityAndRateMustBePositive() {
        var v = valid();
        v.put("quantity", "0");
        v.put("hourlyRate", "-3");
        var errors = OrderValidator.validate(v);
        assertThat(errors.get("quantity")).isNotEmpty();
        assertThat(errors.get("hourlyRate")).isNotEmpty();
        v.put("quantity", "deux");
        assertThat(OrderValidator.validate(v).get("quantity")).contains("must be a whole number");
    }

    @Test
    void reasonMustBeInTheAllowedListAndReplacementNeedsAName() {
        var v = valid();
        v.put("legalReason", "CHEAPER");
        assertThat(OrderValidator.validate(v).get("legalReason")).isNotEmpty();
        v.put("legalReason", "REPLACEMENT");
        assertThat(OrderValidator.validate(v).get("replacedEmployee")).isNotEmpty();
        v.put("replacedEmployee", "Julie Perrin");
        assertThat(OrderValidator.validate(v).get("replacedEmployee")).isEmpty();
    }

    @Test
    void datesMustBeIso() {
        var v = valid();
        v.put("startDate", "5 octobre");
        assertThat(OrderValidator.validate(v).get("startDate")).isNotEmpty();
    }

    @Test
    void typedValuesAreNormalized() {
        assertThat(OrderValidator.normalize("hourlyRate", "12,50 €")).isEqualTo("12.50");
        assertThat(OrderValidator.normalize("hourlyRate", "€12.50")).isEqualTo("12.50");
        assertThat(OrderValidator.normalize("hourlyRate", "12.5 EUR/h")).isEqualTo("12.5");
        assertThat(OrderValidator.normalize("hourlyRate", "1.250,00")).isEqualTo("1250.00");
        assertThat(OrderValidator.normalize("weeklyHours", "35 h")).isEqualTo("35");
        assertThat(OrderValidator.normalize("quantity", " 3 ")).isEqualTo("3");
        assertThat(OrderValidator.normalize("startDate", "05/10/2026")).isEqualTo("2026-10-05");
        assertThat(OrderValidator.normalize("overtimeAllowed", "Oui")).isEqualTo("true");
        assertThat(OrderValidator.normalize("hourlyRate", "douze")).isEqualTo("douze");
    }
}
