package com.flowpanel.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.flowpanel.ai.ValidatableOutput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Structured output of the {@code invoice.extract} prompt. Amounts are checked for internal consistency. */
public record InvoiceLines(
        @JsonProperty(required = true) @JsonPropertyDescription("Invoice number") String invoiceNumber,
        @JsonProperty(required = true) @JsonPropertyDescription("Supplier name") String supplierName,
        @JsonProperty(required = true) List<Line> lines,
        @JsonProperty(required = true) @JsonPropertyDescription("Total excluding VAT, number with a dot") BigDecimal totalExclTax)
        implements ValidatableOutput {

    public record Line(
            @JsonProperty(required = true) @JsonPropertyDescription("Worker full name as written") String workerName,
            @JsonProperty(required = true) @JsonPropertyDescription("Hours invoiced") BigDecimal hours,
            @JsonProperty(required = true) @JsonPropertyDescription("Hourly rate in EUR") BigDecimal hourlyRate,
            @JsonProperty(required = true) @JsonPropertyDescription("Line amount excluding VAT") BigDecimal amount) {
    }

    static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    @Override
    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            errors.add("no invoice line found");
            return errors;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (l.workerName() == null || l.workerName().isBlank() || l.hours() == null || l.hourlyRate() == null || l.amount() == null) {
                errors.add("line " + (i + 1) + " is incomplete");
                continue;
            }
            BigDecimal computed = l.hours().multiply(l.hourlyRate()).setScale(2, RoundingMode.HALF_UP);
            if (computed.subtract(l.amount()).abs().compareTo(TOLERANCE) > 0) {
                errors.add("line " + (i + 1) + ": amount " + l.amount() + " is not hours × rate (" + computed + ")");
            }
            sum = sum.add(l.amount());
        }
        if (totalExclTax == null || sum.subtract(totalExclTax).abs().compareTo(TOLERANCE) > 0) {
            errors.add("total " + totalExclTax + " is not the sum of the lines (" + sum + ")");
        }
        return errors;
    }
}
