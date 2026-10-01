package com.flowpanel.ai.mock;

import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.AiModelClient.ProviderTool;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic stand-in for the LLM for one prompt template id. Responders only see the masked call, exactly
 * like a real provider, so PII restoration is exercised in the mock profile too.
 */
public interface MockResponder {

    /** Prompt template ids this responder answers, e.g. {@code intake.extract}. */
    List<String> features();

    /** Raw model output: JSON for structured calls, plain text otherwise. */
    String respond(ChatCall call, ToolRunner tools);

    /** Lets a mock call the tools offered in the request, as a real model would. */
    interface ToolRunner {

        Optional<ProviderTool> find(String name);

        /** Invokes a tool with JSON arguments and returns its (masked) JSON result. */
        String call(String name, Map<String, Object> arguments);
    }
}
