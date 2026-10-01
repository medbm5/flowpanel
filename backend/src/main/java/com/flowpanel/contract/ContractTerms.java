package com.flowpanel.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Structured fields of a contract, checked by {@link ContractRulesEngine}. */
public record ContractTerms(String position, LocalDate startDate, LocalDate endDate, BigDecimal hourlyRate,
                            BigDecimal weeklyHours, boolean overtimeAllowed, String legalReason, String replacedEmployee,
                            List<String> attachedCertificates) {

    public ContractTerms withDates(LocalDate start, LocalDate end) {
        return new ContractTerms(position, start, end, hourlyRate, weeklyHours, overtimeAllowed, legalReason, replacedEmployee,
                attachedCertificates);
    }

    public ContractTerms withRate(BigDecimal rate) {
        return new ContractTerms(position, startDate, endDate, rate, weeklyHours, overtimeAllowed, legalReason, replacedEmployee,
                attachedCertificates);
    }

    public ContractTerms withReason(String reason, String replaced) {
        return new ContractTerms(position, startDate, endDate, hourlyRate, weeklyHours, overtimeAllowed, reason, replaced,
                attachedCertificates);
    }

    public ContractTerms withCertificates(List<String> certificates) {
        return new ContractTerms(position, startDate, endDate, hourlyRate, weeklyHours, overtimeAllowed, legalReason,
                replacedEmployee, certificates);
    }
}
