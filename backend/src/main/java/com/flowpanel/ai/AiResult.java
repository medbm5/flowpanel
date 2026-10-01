package com.flowpanel.ai;

import java.math.BigDecimal;
import java.util.List;

public record AiResult<T>(T value, String model, int inputTokens, int outputTokens, long latencyMs, BigDecimal costUsd,
                          List<ToolCallTrace> toolCalls, Long aiCallId) {

    public record ToolCallTrace(String name, Object arguments, Object result) {
    }
}
