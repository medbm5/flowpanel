package com.flowpanel.intake;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.flowpanel.ai.ValidatableOutput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structured output of the {@code intake.extract} prompt: every order field with the model's confidence. */
public record OrderExtraction(
        @JsonProperty(required = true) @JsonPropertyDescription("Job title, singular, e.g. Cariste") ExtractedField position,
        @JsonProperty(required = true) @JsonPropertyDescription("Number of workers requested, integer") ExtractedField quantity,
        @JsonProperty(required = true) @JsonPropertyDescription("Work site name") ExtractedField site,
        @JsonProperty(required = true) @JsonPropertyDescription("Start date, ISO yyyy-MM-dd") ExtractedField startDate,
        @JsonProperty(required = true) @JsonPropertyDescription("End date, ISO yyyy-MM-dd") ExtractedField endDate,
        @JsonProperty(required = true) @JsonPropertyDescription("Working days and hours, e.g. Lun–ven 06:00–13:00") ExtractedField schedule,
        @JsonProperty(required = true) @JsonPropertyDescription("Contracted hours per week, number") ExtractedField weeklyHours,
        @JsonProperty(required = true) @JsonPropertyDescription("Gross hourly rate in EUR, number with a dot") ExtractedField hourlyRate,
        @JsonProperty(required = true) @JsonPropertyDescription("ACTIVITY_INCREASE, REPLACEMENT or SEASONAL") ExtractedField legalReason,
        @JsonProperty(required = true) @JsonPropertyDescription("Name of the replaced employee, empty unless REPLACEMENT") ExtractedField replacedEmployee,
        @JsonProperty(required = true) @JsonPropertyDescription("Mandatory certifications, comma separated, empty if none") ExtractedField requiredCertifications,
        @JsonProperty(required = true) @JsonPropertyDescription("true if overtime is agreed, else false") ExtractedField overtimeAllowed)
        implements ValidatableOutput {

    /** One extracted value. Empty string when the email does not say; confidence 0..1. */
    public record ExtractedField(@JsonProperty(required = true) String value,
                                 @JsonProperty(required = true) double confidence) {
    }

    public Map<String, ExtractedField> asMap() {
        Map<String, ExtractedField> m = new LinkedHashMap<>();
        m.put("position", position);
        m.put("quantity", quantity);
        m.put("site", site);
        m.put("startDate", startDate);
        m.put("endDate", endDate);
        m.put("schedule", schedule);
        m.put("weeklyHours", weeklyHours);
        m.put("hourlyRate", hourlyRate);
        m.put("legalReason", legalReason);
        m.put("replacedEmployee", replacedEmployee);
        m.put("requiredCertifications", requiredCertifications);
        m.put("overtimeAllowed", overtimeAllowed);
        return m;
    }

    @Override
    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        asMap().forEach((name, f) -> {
            if (f == null || f.value() == null) {
                errors.add(name + " is missing");
            } else if (f.confidence() < 0 || f.confidence() > 1) {
                errors.add(name + ".confidence must be between 0 and 1");
            }
        });
        return errors;
    }
}
