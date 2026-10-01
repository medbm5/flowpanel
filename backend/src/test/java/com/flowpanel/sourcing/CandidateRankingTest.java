package com.flowpanel.sourcing;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowpanel.sourcing.CandidateRanking.Booking;
import com.flowpanel.sourcing.CandidateRanking.Evaluation;
import com.flowpanel.sourcing.CandidateRanking.OrderCriteria;
import com.flowpanel.sourcing.CandidateRanking.WorkerFacts;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateRankingTest {

    static final OrderCriteria ORDER = new OrderCriteria("Entrepôt Lille Lesquin", LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 16), List.of("CACES R489 cat. 3"), "Cariste");
    static final Geo.Point LESQUIN = new Geo.Point(50.589, 3.115);
    static final Geo.Point DOUAI = new Geo.Point(50.370, 3.080);

    static WorkerFacts worker(List<String> certs, LocalDate to, Geo.Point where, int years) {
        return new WorkerFacts(1L, certs, List.of("cariste"), years, LocalDate.of(2026, 9, 1), to, where, "City");
    }

    @Test
    void eligibleCandidateHasPositiveExplanations() {
        Evaluation e = CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 3"), null, LESQUIN, 7), 0.6, List.of());
        assertThat(e.eligible()).isTrue();
        assertThat(e.exclusionReasons()).isEmpty();
        assertThat(e.explanation()).extracting(CandidateRanking.Item::text)
                .contains("Holds CACES R489 cat. 3", "Available for the full period", "7 years of experience");
        assertThat(e.distanceKm()).isLessThan(1.0);
    }

    @Test
    void missingCertificationExcludes() {
        Evaluation e = CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 1"), null, LESQUIN, 7), 0.6, List.of());
        assertThat(e.eligible()).isFalse();
        assertThat(e.exclusionReasons()).containsExactly("Missing certification: CACES R489 cat. 3");
    }

    @Test
    void certificationSpellingVariantsMatch() {
        Evaluation e = CandidateRanking.evaluate(ORDER, worker(List.of("caces r489 catégorie 3"), null, LESQUIN, 7), 0.6, List.of());
        assertThat(e.eligible()).isTrue();
    }

    @Test
    void partialAvailabilityExcludes() {
        Evaluation e = CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 3"), LocalDate.of(2026, 10, 9), LESQUIN, 5),
                0.6, List.of());
        assertThat(e.exclusionReasons()).containsExactly("Unavailable for the full period (available until 2026-10-09)");
    }

    @Test
    void overlappingPlacementExcludesWithTheMissionRef() {
        List<Booking> bookings = List.of(new Booking("ORD-2026-0142", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 9)));
        Evaluation e = CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 3"), null, LESQUIN, 7), 0.6, bookings);
        assertThat(e.eligible()).isFalse();
        assertThat(e.exclusionReasons().get(0)).startsWith("Already placed on ORD-2026-0142");
    }

    @Test
    void nonOverlappingPlacementDoesNotExclude() {
        List<Booking> bookings = List.of(new Booking("ORD-2026-0100", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 4)));
        assertThat(CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 3"), null, LESQUIN, 7), 0.6, bookings).eligible())
                .isTrue();
    }

    @Test
    void closerMoreExperiencedAndMoreSimilarScoresHigher() {
        double near = CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 3"), null, LESQUIN, 7), 0.5, List.of()).score();
        double far = CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 3"), null, DOUAI, 7), 0.5, List.of()).score();
        double junior = CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 3"), null, LESQUIN, 1), 0.5, List.of()).score();
        double similar = CandidateRanking.evaluate(ORDER, worker(List.of("CACES R489 cat. 3"), null, LESQUIN, 7), 0.9, List.of()).score();
        assertThat(near).isGreaterThan(far).isGreaterThan(0);
        assertThat(near).isGreaterThan(junior);
        assertThat(similar).isGreaterThan(near);
        assertThat(similar).isLessThanOrEqualTo(100);
    }

    @Test
    void geoLocatesTheMostSpecificCity() {
        assertThat(Geo.locate("Entrepôt Lille Lesquin")).contains(new Geo.Point(50.589, 3.115));
        assertThat(Geo.locate("Somewhere")).isEmpty();
    }
}
