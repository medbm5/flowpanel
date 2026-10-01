package com.flowpanel.contract;

import com.flowpanel.intake.Order;
import com.flowpanel.intake.OrderValidator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic compliance checks of a contract against its order. Pure Java, no AI. Every rule is blocking;
 * auto-fixable rules can be corrected from the order (and the worker's certificates) in one click.
 */
public final class ContractRulesEngine {

    public static final String RATE_MATCHES_ORDER = "RATE_MATCHES_ORDER";
    public static final String LEGAL_REASON_PRESENT = "LEGAL_REASON_PRESENT";
    public static final String CERTIFICATE_ATTACHED = "CERTIFICATE_ATTACHED";
    public static final String DATES_WITHIN_ORDER = "DATES_WITHIN_ORDER";

    public record RuleResult(String ruleId, String label, boolean passed, boolean blocking, boolean autoFixable,
                             String message) {
    }

    private ContractRulesEngine() {
    }

    /**
     * @param workerCertificates certificates the worker holds (an absent certificate can only be attached if held)
     */
    public static List<RuleResult> check(ContractTerms c, Order order, List<String> workerCertificates) {
        List<RuleResult> results = new ArrayList<>();

        boolean rateOk = c.hourlyRate() != null && order.hourlyRate() != null && c.hourlyRate().compareTo(order.hourlyRate()) == 0;
        results.add(new RuleResult(RATE_MATCHES_ORDER, "Hourly rate matches the order", rateOk, true, !rateOk,
                rateOk ? "€" + c.hourlyRate() + "/h as ordered"
                        : "Contract rate €" + c.hourlyRate() + "/h differs from the ordered €" + order.hourlyRate() + "/h"));

        boolean reasonValid = c.legalReason() != null && OrderValidator.LEGAL_REASONS.contains(c.legalReason());
        boolean replacementOk = !"REPLACEMENT".equals(c.legalReason())
                || (c.replacedEmployee() != null && !c.replacedEmployee().isBlank());
        boolean reasonOk = reasonValid && replacementOk;
        results.add(new RuleResult(LEGAL_REASON_PRESENT, "Legal reason present", reasonOk, true, !reasonOk,
                reasonOk ? reasonLabel(c.legalReason()) + ("REPLACEMENT".equals(c.legalReason()) ? " — " + c.replacedEmployee() : "")
                        : !reasonValid ? "No valid legal reason for the assignment" : "Replaced employee is missing"));

        Set<String> missing = new LinkedHashSet<>();
        for (String required : order.requiredCertifications()) {
            if (c.attachedCertificates().stream().noneMatch(a -> same(a, required))) {
                missing.add(required);
            }
        }
        boolean certOk = missing.isEmpty();
        boolean certFixable = !certOk && missing.stream().allMatch(m -> workerCertificates.stream().anyMatch(w -> same(w, m)));
        results.add(new RuleResult(CERTIFICATE_ATTACHED, "Required certificate attached", certOk, true, certFixable,
                certOk ? (order.requiredCertifications().isEmpty() ? "No certificate required"
                        : "Attached: " + String.join(", ", c.attachedCertificates()))
                        : "Missing: " + String.join(", ", missing) + (certFixable ? "" : " (the worker does not hold it)")));

        boolean datesOk = c.startDate() != null && c.endDate() != null && !c.startDate().isAfter(c.endDate())
                && !c.startDate().isBefore(order.startDate()) && !c.endDate().isAfter(order.endDate());
        results.add(new RuleResult(DATES_WITHIN_ORDER, "Contract dates within the order period", datesOk, true, !datesOk,
                datesOk ? c.startDate() + " → " + c.endDate()
                        : "Contract " + c.startDate() + " → " + c.endDate() + " is outside the order " + order.startDate() + " → "
                        + order.endDate()));
        return results;
    }

    /** The corrected terms for an auto-fixable rule, or empty when the rule passes or cannot be fixed automatically. */
    public static Optional<ContractTerms> fix(String ruleId, ContractTerms c, Order order, List<String> workerCertificates) {
        Optional<RuleResult> result = check(c, order, workerCertificates).stream().filter(r -> r.ruleId().equals(ruleId)).findFirst();
        if (result.isEmpty() || result.get().passed() || !result.get().autoFixable()) {
            return Optional.empty();
        }
        return Optional.of(switch (ruleId) {
            case RATE_MATCHES_ORDER -> c.withRate(order.hourlyRate());
            case LEGAL_REASON_PRESENT -> c.withReason(order.legalReason(), order.replacedEmployee());
            case CERTIFICATE_ATTACHED -> {
                List<String> attached = new ArrayList<>(c.attachedCertificates());
                for (String required : order.requiredCertifications()) {
                    if (attached.stream().noneMatch(a -> same(a, required))) {
                        attached.add(required);
                    }
                }
                yield c.withCertificates(attached);
            }
            case DATES_WITHIN_ORDER -> {
                var start = c.startDate() == null || c.startDate().isBefore(order.startDate()) ? order.startDate() : c.startDate();
                var end = c.endDate() == null || c.endDate().isAfter(order.endDate()) ? order.endDate() : c.endDate();
                if (start.isAfter(end)) {
                    start = order.startDate();
                    end = order.endDate();
                }
                yield c.withDates(start, end);
            }
            default -> throw new IllegalArgumentException("Unknown rule " + ruleId);
        });
    }

    public static boolean hasBlockingIssue(List<RuleResult> results) {
        return results.stream().anyMatch(r -> r.blocking() && !r.passed());
    }

    static String reasonLabel(String reason) {
        return switch (reason) {
            case "ACTIVITY_INCREASE" -> "Temporary increase in activity";
            case "REPLACEMENT" -> "Replacement of an absent employee";
            case "SEASONAL" -> "Seasonal work";
            default -> reason;
        };
    }

    private static boolean same(String a, String b) {
        return a.replaceAll("[^A-Za-z0-9]", "").equalsIgnoreCase(b.replaceAll("[^A-Za-z0-9]", ""));
    }
}
