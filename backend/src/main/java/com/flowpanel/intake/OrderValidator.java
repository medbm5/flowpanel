package com.flowpanel.intake;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic server-side validation of an order draft. The AI's output is never trusted as-is. */
public final class OrderValidator {

    public static final List<String> FIELDS = List.of("position", "quantity", "site", "startDate", "endDate", "schedule",
            "weeklyHours", "hourlyRate", "legalReason", "replacedEmployee", "requiredCertifications", "overtimeAllowed");

    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("position", "Position"),
            Map.entry("quantity", "Quantity"),
            Map.entry("site", "Site"),
            Map.entry("startDate", "Start date"),
            Map.entry("endDate", "End date"),
            Map.entry("schedule", "Schedule"),
            Map.entry("weeklyHours", "Hours per week"),
            Map.entry("hourlyRate", "Hourly rate (EUR)"),
            Map.entry("legalReason", "Legal reason"),
            Map.entry("replacedEmployee", "Replaced employee"),
            Map.entry("requiredCertifications", "Required certifications"),
            Map.entry("overtimeAllowed", "Overtime agreed"));

    /** Legal grounds for a temporary assignment (French labour code, simplified). */
    public static final List<String> LEGAL_REASONS = List.of("ACTIVITY_INCREASE", "REPLACEMENT", "SEASONAL");

    private OrderValidator() {
    }

    /** Returns validation errors per field (empty list when valid). */
    public static Map<String, List<String>> validate(Map<String, String> values) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        FIELDS.forEach(f -> errors.put(f, new ArrayList<>()));

        required(values, "position", errors);
        required(values, "site", errors);
        required(values, "schedule", errors);

        Integer quantity = parseInt(values.get("quantity"));
        if (quantity == null) {
            errors.get("quantity").add("must be a whole number");
        } else if (quantity <= 0) {
            errors.get("quantity").add("must be greater than 0");
        } else if (quantity > 50) {
            errors.get("quantity").add("must be at most 50 per order");
        }

        LocalDate start = parseDate(values.get("startDate"));
        LocalDate end = parseDate(values.get("endDate"));
        if (start == null) {
            errors.get("startDate").add("must be a date (yyyy-MM-dd)");
        }
        if (end == null) {
            errors.get("endDate").add("must be a date (yyyy-MM-dd)");
        }
        if (start != null && end != null) {
            if (end.isBefore(start)) {
                errors.get("endDate").add("must be on or after the start date");
            } else if (ChronoUnit.MONTHS.between(start, end) >= 18) {
                errors.get("endDate").add("assignment cannot exceed 18 months");
            }
        }

        BigDecimal hours = parseDecimal(values.get("weeklyHours"));
        if (hours == null) {
            errors.get("weeklyHours").add("must be a number");
        } else if (hours.signum() <= 0 || hours.compareTo(BigDecimal.valueOf(48)) > 0) {
            errors.get("weeklyHours").add("must be between 0 and 48");
        }

        BigDecimal rate = parseDecimal(values.get("hourlyRate"));
        if (rate == null) {
            errors.get("hourlyRate").add("must be a number");
        } else if (rate.signum() <= 0) {
            errors.get("hourlyRate").add("must be greater than 0");
        } else if (rate.compareTo(BigDecimal.valueOf(200)) > 0) {
            errors.get("hourlyRate").add("looks too high (over 200 EUR)");
        }

        String reason = values.getOrDefault("legalReason", "");
        if (!LEGAL_REASONS.contains(reason)) {
            errors.get("legalReason").add("must be one of " + String.join(", ", LEGAL_REASONS));
        }
        if ("REPLACEMENT".equals(reason) && blank(values.get("replacedEmployee"))) {
            errors.get("replacedEmployee").add("is required when the reason is REPLACEMENT");
        }

        String overtime = values.getOrDefault("overtimeAllowed", "");
        if (!overtime.equals("true") && !overtime.equals("false")) {
            errors.get("overtimeAllowed").add("must be true or false");
        }
        return errors;
    }

    private static void required(Map<String, String> values, String field, Map<String, List<String>> errors) {
        if (blank(values.get(field))) {
            errors.get(field).add("is required");
        }
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public static Integer parseInt(String s) {
        try {
            return s == null ? null : Integer.valueOf(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate parseDate(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static BigDecimal parseDecimal(String s) {
        try {
            return s == null || s.isBlank() ? null : new BigDecimal(s.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
