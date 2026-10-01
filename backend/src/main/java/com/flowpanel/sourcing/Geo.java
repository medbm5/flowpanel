package com.flowpanel.sourcing;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Small gazetteer of the demo area plus great-circle distance. */
public final class Geo {

    public record Point(double lat, double lon) {
    }

    private static final Map<String, Point> CITIES = new LinkedHashMap<>();

    static {
        // Longest names first so "Villeneuve-d'Ascq" wins over shorter matches.
        CITIES.put("boulogne-billancourt", new Point(48.835, 2.241));
        CITIES.put("villeneuve-d'ascq", new Point(50.623, 3.145));
        CITIES.put("valenciennes", new Point(50.357, 3.523));
        CITIES.put("saint-denis", new Point(48.936, 2.357));
        CITIES.put("tourcoing", new Point(50.724, 3.161));
        CITIES.put("wattrelos", new Point(50.701, 3.216));
        CITIES.put("lesquin", new Point(50.589, 3.115));
        CITIES.put("roubaix", new Point(50.690, 3.181));
        CITIES.put("seclin", new Point(50.548, 3.030));
        CITIES.put("douai", new Point(50.370, 3.080));
        CITIES.put("arras", new Point(50.291, 2.777));
        CITIES.put("croix", new Point(50.678, 3.150));
        CITIES.put("paris", new Point(48.857, 2.352));
        CITIES.put("lille", new Point(50.629, 3.057));
        CITIES.put("lens", new Point(50.432, 2.833));
    }

    private Geo() {
    }

    /** First known city named in a site label, e.g. "Entrepôt Lille Lesquin" → Lesquin (most specific listed first). */
    public static Optional<Point> locate(String site) {
        if (site == null) {
            return Optional.empty();
        }
        String folded = fold(site);
        Point best = null;
        int bestIndex = -1;
        for (Map.Entry<String, Point> e : CITIES.entrySet()) {
            int i = folded.indexOf(fold(e.getKey()));
            if (i >= 0 && i > bestIndex) {
                best = e.getValue();
                bestIndex = i;
            }
        }
        return Optional.ofNullable(best);
    }

    public static double km(Point a, Point b) {
        double r = 6371;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    private static String fold(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
