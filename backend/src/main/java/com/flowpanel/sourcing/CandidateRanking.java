package com.flowpanel.sourcing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic two-stage ranking. Stage 1: hard rules exclude candidates (missing certification, unavailable for the
 * full period, already placed on an overlapping mission). Stage 2: score = weighted embedding similarity + distance
 * + experience. Explanations are generated from the same inputs, never by the LLM.
 */
public final class CandidateRanking {

    public static final double W_SIMILARITY = 0.5;
    public static final double W_DISTANCE = 0.3;
    public static final double W_EXPERIENCE = 0.2;
    public static final double MAX_DISTANCE_KM = 50;
    public static final double GOOD_DISTANCE_KM = 30;
    public static final int GOOD_EXPERIENCE_YEARS = 2;
    public static final double GOOD_SIMILARITY = 0.3;

    private CandidateRanking() {
    }

    public record OrderCriteria(String site, LocalDate start, LocalDate end, List<String> requiredCertifications,
                                String position) {
    }

    public record WorkerFacts(Long workerId, List<String> certifications, List<String> skills, int experienceYears,
                              LocalDate availableFrom, LocalDate availableTo, Geo.Point location, String city) {
    }

    /** An existing placement of the worker on another mission. */
    public record Booking(String missionRef, LocalDate start, LocalDate end) {
    }

    public record Item(boolean ok, String text) {
    }

    public record Evaluation(boolean eligible, List<String> exclusionReasons, double score, double similarity,
                             Double distanceKm, List<Item> explanation) {
    }

    public static Evaluation evaluate(OrderCriteria order, WorkerFacts worker, double similarity, List<Booking> bookings) {
        List<String> exclusions = new ArrayList<>();
        List<Item> items = new ArrayList<>();

        // ---- Stage 1: hard rules
        for (String cert : order.requiredCertifications()) {
            boolean has = worker.certifications().stream().anyMatch(c -> sameCert(c, cert));
            if (has) {
                items.add(new Item(true, "Holds " + cert));
            } else {
                exclusions.add("Missing certification: " + cert);
                items.add(new Item(false, "Missing " + cert));
            }
        }
        boolean availableFromOk = !worker.availableFrom().isAfter(order.start());
        boolean availableToOk = worker.availableTo() == null || !worker.availableTo().isBefore(order.end());
        if (availableFromOk && availableToOk) {
            items.add(new Item(true, "Available for the full period"));
        } else {
            String detail = !availableFromOk ? "available from " + worker.availableFrom() : "available until " + worker.availableTo();
            exclusions.add("Unavailable for the full period (" + detail + ")");
            items.add(new Item(false, "Not available for the full period (" + detail + ")"));
        }
        for (Booking b : bookings) {
            if (overlaps(b.start(), b.end(), order.start(), order.end())) {
                exclusions.add("Already placed on " + b.missionRef() + " (" + b.start() + " → " + b.end() + ")");
                items.add(new Item(false, "Already placed on " + b.missionRef()));
            }
        }

        // ---- Stage 2: score
        Optional<Geo.Point> site = Geo.locate(order.site());
        Double km = site.isPresent() && worker.location() != null ? Geo.km(site.get(), worker.location()) : null;
        double distanceScore = km == null ? 0.5 : Math.max(0, 1 - km / MAX_DISTANCE_KM);
        double experienceScore = Math.min(1, worker.experienceYears() / 8.0);
        double sim = Math.max(0, Math.min(1, similarity));
        double score = 100 * (W_SIMILARITY * sim + W_DISTANCE * distanceScore + W_EXPERIENCE * experienceScore);

        List<String> matchingSkills = matchingSkills(order.position(), worker.skills());
        if (!matchingSkills.isEmpty()) {
            items.add(new Item(true, "Relevant skills: " + String.join(", ", matchingSkills)));
        }
        int simPct = (int) Math.round(sim * 100);
        items.add(sim >= GOOD_SIMILARITY
                ? new Item(true, "Profile matches the order (" + simPct + "% similarity)")
                : new Item(false, "Weak profile match (" + simPct + "% similarity)"));
        if (km == null) {
            items.add(new Item(false, "Distance to site unknown"));
        } else {
            long rounded = Math.round(km);
            String where = worker.city() == null ? "" : " (" + worker.city() + ")";
            items.add(km <= GOOD_DISTANCE_KM ? new Item(true, rounded + " km from the site" + where)
                    : new Item(false, rounded + " km from the site" + where));
        }
        items.add(worker.experienceYears() >= GOOD_EXPERIENCE_YEARS
                ? new Item(true, worker.experienceYears() + " years of experience")
                : new Item(false, "Only " + worker.experienceYears() + " year" + (worker.experienceYears() == 1 ? "" : "s")
                        + " of experience"));

        return new Evaluation(exclusions.isEmpty(), exclusions, round1(score), sim, km == null ? null : round1(km), items);
    }

    public static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return !aStart.isAfter(bEnd) && !bStart.isAfter(aEnd);
    }

    /** "CACES R489 cat. 3" equals "CACES R489 cat 3" etc. */
    static boolean sameCert(String a, String b) {
        return norm(a).equals(norm(b));
    }

    private static String norm(String s) {
        return fold(s).replaceAll("categorie|cat\\.?", "cat").replaceAll("[^a-z0-9]", "");
    }

    private static List<String> matchingSkills(String position, List<String> skills) {
        if (position == null) {
            return List.of();
        }
        String p = fold(position);
        List<String> out = new ArrayList<>();
        for (String skill : skills) {
            String s = fold(skill);
            String stem = s.length() > 5 ? s.substring(0, s.length() - 2) : s;
            if (p.contains(stem) || s.contains(p.length() > 5 ? p.substring(0, p.length() - 2) : p)) {
                out.add(skill);
            }
        }
        return out;
    }

    private static String fold(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
