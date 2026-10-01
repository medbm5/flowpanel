package com.flowpanel.ai;

import java.util.List;

/**
 * The only way to reach an LLM. Every call is PII-masked, rate- and budget-checked (live), recorded in
 * {@code ai_call} and written to the audit trail. The AI extracts, ranks-explains and drafts; it never decides.
 */
public interface AiGateway {

    /** Structured output (JSON schema) mapped to {@code type}; retried once when the output is invalid. */
    <T> AiResult<T> structured(AiPrompt prompt, Class<T> type);

    AiResult<String> text(AiPrompt prompt);

    /** Embeddings for {@code texts} (1536 dimensions in both profiles). */
    AiResult<List<float[]>> embed(String feature, List<String> texts);

    /** Chat with function calling; the result lists the tool calls that were made. */
    AiResult<String> withTools(AiPrompt prompt, List<AiTool> tools);
}
