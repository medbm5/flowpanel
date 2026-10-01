package com.flowpanel.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.ai.mock.MockAiModelClient;
import com.flowpanel.auth.CurrentUser;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.auth.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AiGatewayIT extends AbstractIntegrationTest {

    record Item(@JsonProperty(required = true) String name, @JsonProperty(required = true) int quantity) {
    }

    static final CurrentUser CLAIRE = new CurrentUser(1L, "Claire Dubois", Role.BUYER, 1L, null);

    @Autowired
    AiGateway gateway;
    @Autowired
    AiModelClient client;
    @Autowired
    RequestContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EmbeddingService embeddings;

    @Test
    void everyCallProducesAnAiCallRowAndAnAuditEvent() {
        long calls = count("ai_call");
        long audits = jdbc.queryForObject("select count(*) from audit_event where actor_kind = 'AI'", Long.class);

        AiResult<String> result = context.runAs(CLAIRE, () -> gateway.text(AiPrompt.of("test.echo", null, "sys", "hello")));

        assertThat(count("ai_call")).isEqualTo(calls + 1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where actor_kind = 'AI'", Long.class))
                .isEqualTo(audits + 1);
        Map<String, Object> row = jdbc.queryForMap("select * from ai_call where id = ?", result.aiCallId());
        assertThat(row.get("feature")).isEqualTo("test.echo");
        assertThat(row.get("tenant_id")).isEqualTo(1L);
        assertThat(row.get("profile")).isEqualTo("mock");
        assertThat(row.get("model")).isEqualTo("mock/gpt-4o-mini");
        assertThat(row.get("status")).isEqualTo("OK");
        assertThat((Integer) row.get("input_tokens")).isPositive();
        assertThat(((java.math.BigDecimal) row.get("estimated_cost_usd"))).isPositive();
    }

    @Test
    void piiNeverReachesTheProvider() {
        String email = "Remplacement de Julie Perrin, contact Paul Vasseur 06 98 76 54 32, paul.vasseur@loginord.example. "
                + "Candidat proposé : Karim Haddad.";
        AiResult<String> result = context.runAs(CLAIRE, () -> gateway.text(AiPrompt.of("test.echo", null, "sys", email)
                .withFacts(Map.of("worker", "Karim Haddad"))));

        AiModelClient.ChatCall sent = ((MockAiModelClient) client).recentCalls().getLast();
        assertThat(sent.user() + sent.facts()).doesNotContain("Julie", "Perrin", "Paul Vasseur", "06 98 76 54 32",
                "paul.vasseur@", "Karim", "Haddad");
        assertThat(result.value()).isEqualTo("Echo: " + email);
    }

    @Test
    void invalidStructuredOutputIsRetriedAndBothAttemptsAreRecorded() {
        long before = count("ai_call where feature = 'test.flaky'");
        AiResult<Item> result = context.runAs(CLAIRE, () -> gateway.structured(AiPrompt.of("test.flaky", null, "s", "u"), Item.class));
        assertThat(result.value().quantity()).isEqualTo(1);
        List<String> statuses = jdbc.queryForList(
                "select status from ai_call where feature = 'test.flaky' order by id desc limit 2", String.class);
        assertThat(count("ai_call where feature = 'test.flaky'")).isEqualTo(before + 2);
        assertThat(statuses).containsExactly("OK", "INVALID_OUTPUT");
    }

    @Test
    void embeddingsHave1536DimensionsAndAreCachedByContentHash() {
        String text = "Cariste CACES R489 cat. 3 — test cache " + System.nanoTime();
        long before = count("ai_call where kind = 'EMBEDDING'");
        float[] first = context.runAs(CLAIRE, () -> embeddings.embed("test.embedding", text));
        float[] second = context.runAs(CLAIRE, () -> embeddings.embed("test.embedding", text));
        assertThat(first).hasSize(1536);
        assertThat(second).containsExactly(first);
        assertThat(count("ai_call where kind = 'EMBEDDING'")).isEqualTo(before + 1);
    }

    private long count(String fromWhere) {
        return jdbc.queryForObject("select count(*) from " + fromWhere, Long.class);
    }
}
