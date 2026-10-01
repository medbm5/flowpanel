package com.flowpanel.ai.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.intake.OrderExtraction;
import com.flowpanel.invoice.InvoiceLines;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

class StrictSchemaTest {

    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void refsStandAloneAndObjectsAreClosed() throws Exception {
        for (Class<?> type : new Class<?>[] {OrderExtraction.class, InvoiceLines.class}) {
            JsonNode root = mapper.readTree(StrictSchema.sanitize(new BeanOutputConverter<>(type).getJsonSchema()));
            assertStrict(root);
        }
    }

    private void assertStrict(JsonNode node) {
        if (node.isArray()) {
            node.forEach(this::assertStrict);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.has("$ref")) {
            assertThat(node.size()).as("$ref with siblings: %s", node).isEqualTo(1);
            return;
        }
        if (node.has("properties")) {
            assertThat(node.get("additionalProperties").asBoolean()).isFalse();
            assertThat(node.get("required").size()).isEqualTo(node.get("properties").size());
        }
        node.elements().forEachRemaining(this::assertStrict);
    }
}
