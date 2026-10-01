package com.flowpanel.ai;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI configuration. Model names and prices live here (application.yml / env vars), never in code.
 *
 * @param profile            {@code mock} (default, deterministic, no key) or {@code live} (OpenAI)
 * @param dailyBudgetUsd     live spend allowed per UTC day, computed from ai_call records
 * @param rateLimitPerMinute live requests allowed per tenant per minute
 * @param pricing            USD per 1M tokens, keyed by model name
 */
@ConfigurationProperties(prefix = "flowpanel.ai")
public record AiProperties(
        @DefaultValue("mock") String profile,
        String apiKey,
        @DefaultValue("gpt-4o-mini") String chatModel,
        @DefaultValue("text-embedding-3-small") String embeddingModel,
        @DefaultValue("2") BigDecimal dailyBudgetUsd,
        @DefaultValue("30") int rateLimitPerMinute,
        @DefaultValue("800") int defaultMaxTokens,
        @DefaultValue("0.75") double confidenceThreshold,
        Map<String, Price> pricing) {

    public record Price(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
    }

    public boolean live() {
        return "live".equalsIgnoreCase(profile);
    }

    public String profileName() {
        return live() ? "live" : "mock";
    }

    /** Price of a model; mock calls ({@code mock/<model>}) are priced like the model they simulate. */
    public Price priceOf(String model) {
        String key = model != null && model.startsWith("mock/") ? model.substring(5) : model;
        if (pricing == null || key == null) {
            return new Price(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return pricing.getOrDefault(key, new Price(BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
