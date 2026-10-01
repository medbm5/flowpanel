package com.flowpanel.ai.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Makes a generated JSON schema acceptable to OpenAI's strict structured-output mode: a {@code $ref} must stand alone
 * (no sibling keywords such as {@code description}), every object lists all its properties as required and forbids
 * additional properties.
 */
final class StrictSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StrictSchema() {
    }

    static String sanitize(String schema) {
        try {
            JsonNode root = MAPPER.readTree(schema);
            clean(root);
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return schema;
        }
    }

    private static void clean(JsonNode node) {
        if (node.isArray()) {
            node.forEach(StrictSchema::clean);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode obj = (ObjectNode) node;
        if (obj.has("$ref")) {
            List<String> extra = new ArrayList<>();
            obj.fieldNames().forEachRemaining(f -> {
                if (!f.equals("$ref")) {
                    extra.add(f);
                }
            });
            obj.remove(extra);
            return;
        }
        JsonNode properties = obj.get("properties");
        if (properties != null && properties.isObject()) {
            var required = MAPPER.createArrayNode();
            properties.fieldNames().forEachRemaining(required::add);
            obj.set("required", required);
            obj.put("additionalProperties", false);
        }
        for (Iterator<JsonNode> it = obj.elements(); it.hasNext(); ) {
            clean(it.next());
        }
    }
}
