package com.flowpanel.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowpanel.invoice.ThreeWayMatcher.Expected;
import com.flowpanel.invoice.ThreeWayMatcher.Invoiced;
import com.flowpanel.invoice.ThreeWayMatcher.Result;
import com.flowpanel.invoice.ThreeWayMatcher.Status;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThreeWayMatcherTest {

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static final List<Expected> EXPECTED = List.of(
            new Expected("Karim Haddad", d("13.20"), d("111")),
            new Expected("Lucas Petit", d("13.20"), d("105")));

    @Test
    void exactMatch() {
        Result r = ThreeWayMatcher.match(EXPECTED, List.of(
                new Invoiced("Karim Haddad", d("111"), d("13.20"), d("1465.20")),
                new Invoiced("Lucas Petit", d("105.00"), d("13.2"), d("1386.00"))));
        assertThat(r.matched()).isTrue();
        assertThat(r.expectedTotal()).isEqualByComparingTo("2851.20");
        assertThat(r.invoicedTotal()).isEqualByComparingTo("2851.20");
        assertThat(r.overbilled()).isEqualByComparingTo("0");
    }

    @Test
    void hourMismatch() {
        Result r = ThreeWayMatcher.match(EXPECTED, List.of(
                new Invoiced("Karim Haddad", d("114"), d("13.20"), d("1504.80")),
                new Invoiced("Lucas Petit", d("105"), d("13.20"), d("1386.00"))));
        assertThat(r.matched()).isFalse();
        var karim = r.lines().get(0);
        assertThat(karim.status()).isEqualTo(Status.HOURS_MISMATCH);
        assertThat(karim.message()).isEqualTo("114 h invoiced vs 111 h approved");
        assertThat(karim.delta()).isEqualByComparingTo("39.60");
        assertThat(r.overbilled()).isEqualByComparingTo("39.60");
        assertThat(r.lines().get(1).status()).isEqualTo(Status.MATCH);
    }

    @Test
    void rateMismatch() {
        Result r = ThreeWayMatcher.match(EXPECTED, List.of(
                new Invoiced("Karim Haddad", d("111"), d("13.50"), d("1498.50")),
                new Invoiced("Lucas Petit", d("105"), d("13.20"), d("1386.00"))));
        var karim = r.lines().get(0);
        assertThat(karim.status()).isEqualTo(Status.RATE_MISMATCH);
        assertThat(karim.message()).contains("13.5").contains("13.2");
        assertThat(karim.delta()).isEqualByComparingTo("33.30");
    }

    @Test
    void roundingIsHalfUpToTheCent() {
        // 37.5 h × 12.15 = 455.625 → 455.63
        List<Expected> expected = List.of(new Expected("Inès Moreau", d("12.15"), d("37.5")));
        assertThat(expected.get(0).amount()).isEqualByComparingTo("455.63");
        assertThat(ThreeWayMatcher.match(expected, List.of(new Invoiced("Ines MOREAU", d("37.5"), d("12.15"), d("455.63")))).matched())
                .isTrue();
        Result off = ThreeWayMatcher.match(expected, List.of(new Invoiced("Inès Moreau", d("37.5"), d("12.15"), d("455.62"))));
        assertThat(off.lines().get(0).status()).isEqualTo(Status.AMOUNT_MISMATCH);
        assertThat(off.lines().get(0).delta()).isEqualByComparingTo("-0.01");
    }

    @Test
    void unknownWorkerAndMissingLineAreReported() {
        Result r = ThreeWayMatcher.match(EXPECTED, List.of(new Invoiced("Someone Else", d("10"), d("13.20"), d("132.00"))));
        assertThat(r.lines()).extracting(ThreeWayMatcher.LineResult::status)
                .containsExactly(Status.UNKNOWN_WORKER, Status.MISSING_LINE, Status.MISSING_LINE);
        assertThat(r.matched()).isFalse();
    }
}
