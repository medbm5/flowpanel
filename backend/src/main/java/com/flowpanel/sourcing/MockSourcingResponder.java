package com.flowpanel.sourcing;

import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.mock.MockResponder;
import java.util.List;
import org.springframework.stereotype.Component;

/** Mock profile answer for {@code sourcing.summary}: a one-sentence summary built from the given facts only. */
@Component
public class MockSourcingResponder implements MockResponder {

    @Override
    public List<String> features() {
        return List.of(SourcingService.SUMMARY_FEATURE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String respond(ChatCall call, ToolRunner tools) {
        List<String> positives = (List<String>) call.facts().getOrDefault("positives", List.of());
        List<String> negatives = (List<String>) call.facts().getOrDefault("negatives", List.of());
        String strengths = positives.stream().limit(3).map(MockSourcingResponder::lower).reduce((a, b) -> a + ", " + b).orElse("");
        String sentence = (negatives.isEmpty() ? "Strong fit: " : "Good fit: ") + strengths;
        if (!negatives.isEmpty()) {
            sentence += "; watch point: " + lower(negatives.get(0));
        }
        return sentence + ".";
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
