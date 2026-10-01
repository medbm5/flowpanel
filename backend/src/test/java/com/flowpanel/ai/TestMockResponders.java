package com.flowpanel.ai;

import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.mock.MockResponder;
import java.util.List;
import org.springframework.stereotype.Component;

/** Test-only mock responses (picked up by component scanning from test sources). */
@Component
public class TestMockResponders implements MockResponder {

    @Override
    public List<String> features() {
        return List.of("test.echo", "test.structured", "test.flaky");
    }

    @Override
    public String respond(ChatCall call, ToolRunner tools) {
        return switch (call.feature()) {
            case "test.echo" -> "Echo: " + call.user();
            case "test.structured" -> "{\"name\":\"" + call.facts().getOrDefault("name", "x") + "\",\"quantity\":2}";
            case "test.flaky" -> call.user().contains("previous answer was rejected")
                    ? "{\"name\":\"ok\",\"quantity\":1}" : "{\"name\":\"ok\"";
            default -> throw new IllegalArgumentException(call.feature());
        };
    }
}
