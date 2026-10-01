package com.flowpanel.ai;

import java.util.List;
import java.util.Map;

/**
 * A prompt sent through the {@link AiGateway}.
 *
 * @param feature   prompt template id, e.g. {@code intake.extract}; keys metrics and mock responses
 * @param missionId mission the call belongs to (nullable)
 * @param system    system instructions
 * @param user      user content (may contain PII; it is masked before it leaves the gateway)
 * @param facts     structured inputs the prompt was built from (also masked); mock responders read them
 * @param maxTokens output token cap; the gateway default applies when null
 * @param extraNames additional person names to mask for this call
 */
public record AiPrompt(String feature, Long missionId, String system, String user, Map<String, Object> facts,
                       Integer maxTokens, List<String> extraNames) {

    public AiPrompt {
        facts = facts == null ? Map.of() : facts;
        extraNames = extraNames == null ? List.of() : extraNames;
    }

    public static AiPrompt of(String feature, Long missionId, String system, String user) {
        return new AiPrompt(feature, missionId, system, user, Map.of(), null, List.of());
    }

    public AiPrompt withFacts(Map<String, Object> newFacts) {
        return new AiPrompt(feature, missionId, system, user, newFacts, maxTokens, extraNames);
    }

    public AiPrompt withMaxTokens(int tokens) {
        return new AiPrompt(feature, missionId, system, user, facts, tokens, extraNames);
    }

    public AiPrompt withUser(String newUser) {
        return new AiPrompt(feature, missionId, system, newUser, facts, maxTokens, extraNames);
    }
}
