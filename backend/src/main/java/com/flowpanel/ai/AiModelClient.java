package com.flowpanel.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Provider SPI behind the gateway: receives already-masked content only. Implementations: the deterministic
 * {@code MockAiModelClient} (mock profile) and {@code SpringAiModelClient} (live profile, OpenAI via Spring AI).
 */
public interface AiModelClient {

    String profile();

    String chatModel();

    String embeddingModel();

    ChatOutcome chat(ChatCall call);

    EmbeddingOutcome embed(List<String> texts);

    /** A tool as seen by the provider: JSON in, JSON out. Masking and tracing are applied by the gateway. */
    record ProviderTool(String name, String description, String inputSchema, Function<String, String> invoke) {
    }

    /**
     * @param jsonSchema   non-null for structured output
     * @param responseType target type of a structured call (mock responders use it), null otherwise
     */
    record ChatCall(String feature, String system, String user, Map<String, Object> facts, String jsonSchema,
                    Class<?> responseType, int maxTokens, List<ProviderTool> tools) {
    }

    /** @param simulatedLatencyMs set by the mock client; the gateway measures wall time otherwise */
    record ChatOutcome(String text, String model, int inputTokens, int outputTokens, Long simulatedLatencyMs) {
    }

    record EmbeddingOutcome(List<float[]> vectors, String model, int inputTokens, Long simulatedLatencyMs) {
    }
}
