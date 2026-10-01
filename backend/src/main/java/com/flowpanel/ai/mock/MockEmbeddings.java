package com.flowpanel.ai.mock;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Deterministic hash-based embeddings (feature hashing of normalized word stems and stem bigrams), L2-normalized,
 * with the same 1536 dimensions as text-embedding-3-small so the schema is identical across profiles. Lexically
 * similar texts get similar vectors, which keeps retrieval and ranking meaningful in the mock profile.
 */
public final class MockEmbeddings {

    public static final int DIMENSIONS = 1536;

    private static final Set<String> STOP_WORDS = Set.of(
            "les", "des", "une", "est", "pour", "dans", "par", "sur", "avec", "aux", "que", "qui", "pas", "plus", "the",
            "and", "for", "with", "are", "our", "ses", "son", "sont", "nous", "vous", "this", "that", "what", "how",
            "quel", "quelle", "quels", "quelles", "comment", "est-ce", "elle", "ils", "leur", "ont", "été", "être",
            "faire", "fait", "doit", "peut", "tout", "tous", "toute", "toutes", "also", "from", "does", "can", "which");

    private MockEmbeddings() {
    }

    public static float[] embed(String text) {
        float[] v = new float[DIMENSIONS];
        String previous = null;
        for (String raw : normalize(text).split("[^a-z0-9]+")) {
            if (raw.length() < 3 || STOP_WORDS.contains(raw)) {
                continue;
            }
            String stem = stem(raw);
            add(v, stem, 1.0f);
            if (previous != null) {
                add(v, previous + "_" + stem, 0.5f);
            }
            previous = stem;
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1;
            return v;
        }
        float inv = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < v.length; i++) {
            v[i] *= inv;
        }
        return v;
    }

    private static void add(float[] v, String feature, float weight) {
        CRC32 crc = new CRC32();
        crc.update(feature.getBytes(StandardCharsets.UTF_8));
        long h = crc.getValue();
        int index = (int) (h % DIMENSIONS);
        float sign = ((h >> 20) & 1) == 0 ? 1f : -1f;
        v[index] += sign * weight;
    }

    static String normalize(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Very small French/English stemmer: enough to match plurals and common suffixes. */
    static String stem(String word) {
        String w = word;
        for (String suffix : new String[] {"ements", "ement", "ations", "ation", "eurs", "euse", "eur", "ees", "es", "s", "e"}) {
            if (w.length() > suffix.length() + 3 && w.endsWith(suffix)) {
                w = w.substring(0, w.length() - suffix.length());
                break;
            }
        }
        return w;
    }

    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }
}
