package com.flowpanel.intake;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.mock.MockResponder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mock profile answer for {@code intake.extract}: the rule-based {@link HeuristicOrderExtractor} applied to the
 * (masked) email. Test scenario markers: {@code #mock-invalid-once} returns broken JSON on the first attempt,
 * {@code #mock-invalid-always} on every attempt.
 */
@Component
public class MockIntakeResponder implements MockResponder {

    private static final String RETRY_MARKER = "Your previous answer was rejected";

    private final ObjectMapper mapper;

    public MockIntakeResponder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> features() {
        return List.of(IntakeService.FEATURE);
    }

    @Override
    public String respond(ChatCall call, ToolRunner tools) {
        String user = call.user();
        boolean retry = user.contains(RETRY_MARKER);
        String email = retry ? user.substring(0, user.indexOf(RETRY_MARKER)) : user;
        if (email.contains("#mock-invalid-always") || (email.contains("#mock-invalid-once") && !retry)) {
            return "{\"position\": {\"value\": \"Cariste\"";
        }
        try {
            return mapper.writeValueAsString(HeuristicOrderExtractor.extract(email));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
