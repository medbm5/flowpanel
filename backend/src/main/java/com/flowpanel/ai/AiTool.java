package com.flowpanel.ai;

import java.util.Map;
import java.util.function.Function;

/**
 * A function the LLM may call. Handlers run in the caller's thread and security context, so they apply the same
 * tenant / supplier scoping as any other service call.
 *
 * @param inputSchema JSON schema of the arguments object
 */
public record AiTool(String name, String description, String inputSchema, Function<Map<String, Object>, Object> handler) {

    public static final String NO_ARGS = "{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":false}";
}
