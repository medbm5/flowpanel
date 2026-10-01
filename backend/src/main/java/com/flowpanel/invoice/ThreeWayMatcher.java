package com.flowpanel.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Three-way match of contract rate, approved hours and invoice lines, line by line, in BigDecimal.
 * Expected amount = approved hours × contract rate, rounded half-up to the cent. Pure Java, no AI.
 */
public final class ThreeWayMatcher {

    public enum Status { MATCH, HOURS_MISMATCH, RATE_MISMATCH, AMOUNT_MISMATCH, UNKNOWN_WORKER, MISSING_LINE }

    /** What the buyer expects to pay for one worker: from the signed contract and the approved timesheets. */
    public record Expected(String workerName, BigDecimal contractRate, BigDecimal approvedHours) {

        public BigDecimal amount() {
            return money(approvedHours.multiply(contractRate));
        }
    }

    public record Invoiced(String workerName, BigDecimal hours, BigDecimal rate, BigDecimal amount) {
    }

    public record LineResult(String workerName, Status status, BigDecimal expectedHours, BigDecimal invoicedHours,
                             BigDecimal expectedRate, BigDecimal invoicedRate, BigDecimal expectedAmount,
                             BigDecimal invoicedAmount, BigDecimal delta, String message) {
    }

    public record Result(List<LineResult> lines, BigDecimal expectedTotal, BigDecimal invoicedTotal, BigDecimal overbilled) {

        public boolean matched() {
            return lines.stream().allMatch(l -> l.status() == Status.MATCH);
        }
    }

    private ThreeWayMatcher() {
    }

    public static Result match(List<Expected> expected, List<Invoiced> invoiced) {
        List<LineResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BigDecimal expectedTotal = BigDecimal.ZERO;
        BigDecimal invoicedTotal = BigDecimal.ZERO;
        BigDecimal overbilled = BigDecimal.ZERO;
        for (Invoiced line : invoiced) {
            BigDecimal invoicedAmount = money(line.amount());
            invoicedTotal = invoicedTotal.add(invoicedAmount);
            Expected e = expected.stream().filter(x -> sameName(x.workerName(), line.workerName())).findFirst().orElse(null);
            if (e == null) {
                results.add(new LineResult(line.workerName(), Status.UNKNOWN_WORKER, null, line.hours(), null, line.rate(), null,
                        invoicedAmount, invoicedAmount, "No contract or approved hours for this worker"));
                overbilled = overbilled.add(invoicedAmount);
                continue;
            }
            seen.add(key(e.workerName()));
            BigDecimal expectedAmount = e.amount();
            expectedTotal = expectedTotal.add(expectedAmount);
            BigDecimal delta = invoicedAmount.subtract(expectedAmount);
            Status status;
            String message;
            if (line.hours().compareTo(e.approvedHours()) != 0) {
                status = Status.HOURS_MISMATCH;
                message = fmt(line.hours()) + " h invoiced vs " + fmt(e.approvedHours()) + " h approved";
            } else if (line.rate().compareTo(e.contractRate()) != 0) {
                status = Status.RATE_MISMATCH;
                message = "€" + fmt(line.rate()) + "/h invoiced vs €" + fmt(e.contractRate()) + "/h in the contract";
            } else if (invoicedAmount.compareTo(money(line.hours().multiply(line.rate()))) != 0 || delta.signum() != 0) {
                status = Status.AMOUNT_MISMATCH;
                message = "Amount €" + invoicedAmount.toPlainString() + " differs from €" + expectedAmount.toPlainString();
            } else {
                status = Status.MATCH;
                message = "Matches contract and approved hours";
            }
            if (delta.signum() > 0) {
                overbilled = overbilled.add(delta);
            }
            results.add(new LineResult(e.workerName(), status, e.approvedHours(), line.hours(), e.contractRate(), line.rate(),
                    expectedAmount, invoicedAmount, delta, message));
        }
        for (Expected e : expected) {
            if (!seen.contains(key(e.workerName()))) {
                BigDecimal expectedAmount = e.amount();
                expectedTotal = expectedTotal.add(expectedAmount);
                results.add(new LineResult(e.workerName(), Status.MISSING_LINE, e.approvedHours(), null, e.contractRate(), null,
                        expectedAmount, null, expectedAmount.negate(), "Approved hours are not invoiced"));
            }
        }
        return new Result(results, expectedTotal, invoicedTotal, overbilled);
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static boolean sameName(String a, String b) {
        return key(a).equals(key(b));
    }

    private static String key(String name) {
        return Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^a-z]", "");
    }

    private static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
