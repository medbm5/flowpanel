package com.flowpanel.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Embeddings with a content-hash cache: unchanged content is never embedded twice for the same model. */
@Service
public class EmbeddingService {

    private final AiGateway gateway;
    private final AiModelClient client;
    private final JdbcTemplate jdbc;

    public EmbeddingService(AiGateway gateway, AiModelClient client, JdbcTemplate jdbc) {
        this.gateway = gateway;
        this.client = client;
        this.jdbc = jdbc;
    }

    public float[] embed(String feature, String text) {
        return embedAll(feature, List.of(text)).get(0);
    }

    /** Returns one vector per input, in order; only cache misses go through the gateway. */
    public List<float[]> embedAll(String feature, List<String> texts) {
        String model = client.embeddingModel();
        Map<String, float[]> byHash = new HashMap<>();
        Set<String> missingHashes = new LinkedHashSet<>();
        List<String> missingTexts = new ArrayList<>();
        for (String text : texts) {
            String hash = DefaultAiGateway.hash(text);
            if (byHash.containsKey(hash) || missingHashes.contains(hash)) {
                continue;
            }
            List<String> cached = jdbc.queryForList(
                    "select embedding::text from embedding_cache where content_hash = ? and model = ?",
                    String.class, hash, model);
            if (cached.isEmpty()) {
                missingHashes.add(hash);
                missingTexts.add(text);
            } else {
                byHash.put(hash, parse(cached.get(0)));
            }
        }
        if (!missingTexts.isEmpty()) {
            List<float[]> vectors = gateway.embed(feature, missingTexts).value();
            List<String> hashes = new ArrayList<>(missingHashes);
            for (int i = 0; i < hashes.size(); i++) {
                byHash.put(hashes.get(i), vectors.get(i));
                jdbc.update("insert into embedding_cache (content_hash, model, embedding) values (?, ?, cast(? as vector)) "
                        + "on conflict do nothing", hashes.get(i), model, literal(vectors.get(i)));
            }
        }
        return texts.stream().map(t -> byHash.get(DefaultAiGateway.hash(t))).toList();
    }

    public String model() {
        return client.embeddingModel();
    }

    /** pgvector text literal, e.g. {@code [0.1,0.2]}. */
    public static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    public static float[] parse(String literal) {
        String body = literal.substring(1, literal.length() - 1);
        String[] parts = body.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Float.parseFloat(parts[i]);
        }
        return v;
    }
}
