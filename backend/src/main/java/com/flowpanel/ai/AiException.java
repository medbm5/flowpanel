package com.flowpanel.ai;

import com.flowpanel.common.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Typed AI failures. The {@code code} property lets the UI show a specific message. */
public class AiException extends ApiException {

    private final String code;

    private AiException(HttpStatus status, String title, String code, String detail) {
        super(status, title, detail, Map.of("code", code));
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AiException budgetExceeded(String budget) {
        return new AiException(HttpStatus.TOO_MANY_REQUESTS, "AI daily budget reached", "ai_budget_exceeded",
                "The daily AI budget of $" + budget + " is used up. Live AI features are paused until tomorrow (UTC).");
    }

    public static AiException rateLimited() {
        return new AiException(HttpStatus.TOO_MANY_REQUESTS, "Too many AI requests", "ai_rate_limited",
                "Too many AI requests for your organization in the last minute. Please retry shortly.");
    }

    public static AiException invalidOutput(String feature, String error) {
        return new AiException(HttpStatus.BAD_GATEWAY, "AI output invalid", "ai_invalid_output",
                "The AI returned an invalid result for " + feature + " twice (" + error + "). Please retry or fill the data manually.");
    }

    public static AiException providerError(String feature, String error) {
        return new AiException(HttpStatus.BAD_GATEWAY, "AI provider error", "ai_provider_error",
                "The AI provider failed for " + feature + ": " + error);
    }
}
