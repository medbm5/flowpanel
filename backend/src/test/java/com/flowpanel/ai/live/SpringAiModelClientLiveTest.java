package com.flowpanel.ai.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.AiModelClient.ChatOutcome;
import com.flowpanel.ai.AiModelClient.ProviderTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Opt-in smoke test against the real OpenAI API (a few hundred tokens, well under $0.01).
 * Run with {@code OPENAI_LIVE_SMOKE=true OPENAI_API_KEY=... ./mvnw test -Dtest=SpringAiModelClientLiveTest}.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_LIVE_SMOKE", matches = "true")
class SpringAiModelClientLiveTest {

    record Field(@JsonProperty(required = true) String value, @JsonProperty(required = true) double confidence) {
    }

    record Extraction(@JsonProperty(required = true) Field position, @JsonProperty(required = true) Field quantity) {
    }

    private final SpringAiModelClient client = new SpringAiModelClient(System.getenv("OPENAI_API_KEY"),
            System.getenv().getOrDefault("OPENAI_CHAT_MODEL", "gpt-4o-mini"), "text-embedding-3-small");

    @Test
    void structuredOutputWithJsonSchema() {
        String schema = new BeanOutputConverter<>(Extraction.class).getJsonSchema();
        ChatOutcome out = client.chat(new ChatCall("smoke", "Extract fields. Confidence between 0 and 1.",
                "Nous avons besoin de 2 caristes.", Map.of(), schema, Extraction.class, 120, List.of()));
        Extraction e = new BeanOutputConverter<>(Extraction.class).convert(out.text());
        assertThat(e.quantity().value()).contains("2");
        assertThat(out.inputTokens()).isPositive();
    }

    @Test
    void toolCalling() throws Exception {
        List<String> calls = new ArrayList<>();
        ProviderTool tool = new ProviderTool("getOpenAnomalies", "Number of open timesheet anomalies",
                "{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}", json -> {
                    calls.add(json);
                    return "{\"openAnomalies\":3}";
                });
        ChatOutcome out = client.chat(new ChatCall("smoke", "Use tools to answer. Be brief.",
                "How many open anomalies are there?", Map.of(), null, null, 60, List.of(tool)));
        assertThat(calls).isNotEmpty();
        assertThat(out.text()).contains("3");
    }

    @Test
    void embeddingsHave1536Dimensions() {
        var out = client.embed(List.of("cariste CACES 3"));
        assertThat(out.vectors().get(0)).hasSize(1536);
        assertThat(new ObjectMapper()).isNotNull();
    }
}
