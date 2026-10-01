package com.flowpanel.intake;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** The confirmed order, typed. Built from the reviewed draft; consumed by sourcing, contracts, timesheets, invoice. */
public record Order(String position, int quantity, String site, LocalDate startDate, LocalDate endDate, String schedule,
                    BigDecimal weeklyHours, BigDecimal hourlyRate, String legalReason, String replacedEmployee,
                    List<String> requiredCertifications, boolean overtimeAllowed) {

    static Order from(Map<String, String> v) {
        List<String> certs = v.getOrDefault("requiredCertifications", "").isBlank() ? List.of()
                : Arrays.stream(v.get("requiredCertifications").split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        return new Order(v.get("position"), OrderValidator.parseInt(v.get("quantity")), v.get("site"),
                OrderValidator.parseDate(v.get("startDate")), OrderValidator.parseDate(v.get("endDate")), v.get("schedule"),
                OrderValidator.parseDecimal(v.get("weeklyHours")), OrderValidator.parseDecimal(v.get("hourlyRate")),
                v.get("legalReason"), v.getOrDefault("replacedEmployee", ""), certs,
                "true".equals(v.get("overtimeAllowed")));
    }

    public Map<String, Object> toPayload() {
        return Map.ofEntries(
                Map.entry("position", position),
                Map.entry("quantity", quantity),
                Map.entry("site", site),
                Map.entry("startDate", String.valueOf(startDate)),
                Map.entry("endDate", String.valueOf(endDate)),
                Map.entry("schedule", schedule),
                Map.entry("weeklyHours", weeklyHours),
                Map.entry("hourlyRate", hourlyRate),
                Map.entry("legalReason", legalReason),
                Map.entry("replacedEmployee", replacedEmployee == null ? "" : replacedEmployee),
                Map.entry("requiredCertifications", requiredCertifications),
                Map.entry("overtimeAllowed", overtimeAllowed));
    }
}
