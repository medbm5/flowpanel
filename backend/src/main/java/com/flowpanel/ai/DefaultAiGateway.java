package com.flowpanel.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.AiModelClient.ChatOutcome;
import com.flowpanel.ai.AiModelClient.EmbeddingOutcome;
import com.flowpanel.ai.AiModelClient.ProviderTool;
import com.flowpanel.ai.AiResult.ToolCallTrace;
import com.flowpanel.auth.CurrentUser;
import com.flowpanel.auth.RequestContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * Gateway implementation shared by both AI profiles: the profile only changes the {@link AiModelClient}
 * (mock or OpenAI), so masking, spend protection, retries, metrics and audit are exercised identically in tests.
 */
@Service
public class DefaultAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiGateway.class);
    private static final int MAX_ATTEMPTS = 2;

    private final AiModelClient client;
    private final AiCallRecorder recorder;
    private final SpendGuard spendGuard;
    private final PiiDirectory piiDirectory;
    private final RequestContext context;
    private final AiProperties props;
    private final Validator validator;
    private final ObjectMapper mapper;

    public DefaultAiGateway(AiModelClient client, AiCallRecorder recorder, SpendGuard spendGuard,
                            PiiDirectory piiDirectory, RequestContext context, AiProperties props, Validator validator,
                            ObjectMapper mapper) {
        this.client = client;
        this.recorder = recorder;
        this.spendGuard = spendGuard;
        this.piiDirectory = piiDirectory;
        this.context = context;
        this.props = props;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Override
    public <T> AiResult<T> structured(AiPrompt prompt, Class<T> type) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        String schema = converter.getJsonSchema();
        String lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            AiPrompt effective = lastError == null ? prompt : prompt.withUser(prompt.user()
                    + "\n\nYour previous answer was rejected: " + lastError
                    + "\nReturn only JSON that matches the schema and fixes this error.");
            Exchange exchange = exchange(effective, AiCall.Kind.STRUCTURED, schema, type, List.of(), attempt);
            T value;
            try {
                value = converter.convert(exchange.text());
                List<String> errors = validate(value);
                if (!errors.isEmpty()) {
                    throw new IllegalArgumentException(String.join("; ", errors));
                }
            } catch (RuntimeException e) {
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : firstLine(e.getMessage());
                recorder.record(exchange.attempt(AiCall.Status.INVALID_OUTPUT, lastError));
                log.warn("Invalid structured output for {} (attempt {}): {}", prompt.feature(), attempt, lastError);
                continue;
            }
            AiCall call = recorder.record(exchange.attempt(AiCall.Status.OK, null));
            return exchange.result(value, call, List.of());
        }
        throw AiException.invalidOutput(prompt.feature(), lastError);
    }

    @Override
    public AiResult<String> text(AiPrompt prompt) {
        Exchange exchange = exchange(prompt, AiCall.Kind.TEXT, null, null, List.of(), 1);
        AiCall call = recorder.record(exchange.attempt(AiCall.Status.OK, null));
        return exchange.result(exchange.text(), call, List.of());
    }

    @Override
    public AiResult<String> withTools(AiPrompt prompt, List<AiTool> tools) {
        Exchange exchange = exchange(prompt, AiCall.Kind.TOOLS, null, null, tools, 1);
        AiCall call = recorder.record(exchange.attempt(AiCall.Status.OK, null));
        return exchange.result(exchange.text(), call, exchange.traces());
    }

    @Override
    public AiResult<List<float[]>> embed(String feature, List<String> texts) {
        String model = client.embeddingModel();
        String hash = hash(String.join("\n", texts));
        guard(feature, null, AiCall.Kind.EMBEDDING, model, hash);
        PiiMasker.Session session = PiiMasker.session(piiDirectory.knownNames());
        List<String> masked = texts.stream().map(session::mask).toList();
        long start = System.nanoTime();
        EmbeddingOutcome outcome;
        try {
            outcome = client.embed(masked);
        } catch (RuntimeException e) {
            recorder.record(new AiCallRecorder.Attempt(feature, null, AiCall.Kind.EMBEDDING, model, 0, 0,
                    elapsed(start), AiCall.Status.ERROR, 1, e.getMessage(), hash));
            throw AiException.providerError(feature, firstLine(String.valueOf(e.getMessage())));
        }
        long latency = outcome.simulatedLatencyMs() != null ? outcome.simulatedLatencyMs() : elapsed(start);
        AiCall call = recorder.record(new AiCallRecorder.Attempt(feature, null, AiCall.Kind.EMBEDDING, outcome.model(),
                outcome.inputTokens(), 0, latency, AiCall.Status.OK, 1, null, hash));
        return new AiResult<>(outcome.vectors(), outcome.model(), outcome.inputTokens(), 0, latency,
                call.getEstimatedCostUsd(), List.of(), call.getId());
    }

    // ------------------------------------------------------------------ internals

    /** One masked round trip to the provider; failures are recorded and surfaced as typed errors. */
    private Exchange exchange(AiPrompt prompt, AiCall.Kind kind, String schema, Class<?> type, List<AiTool> tools,
                              int attempt) {
        List<String> names = new ArrayList<>(piiDirectory.knownNames());
        names.addAll(prompt.extraNames());
        PiiMasker.Session session = PiiMasker.session(names);
        String system = session.mask(prompt.system());
        String user = session.mask(prompt.user());
        Map<String, Object> facts = session.maskFacts(prompt.facts());
        String hash = hash(system + "\n" + user);
        String model = client.chatModel();
        guard(prompt.feature(), prompt.missionId(), kind, model, hash);

        List<ToolCallTrace> traces = Collections.synchronizedList(new ArrayList<>());
        List<ProviderTool> providerTools = tools.stream().map(t -> providerTool(t, session, traces)).toList();
        int maxTokens = prompt.maxTokens() != null ? prompt.maxTokens() : props.defaultMaxTokens();
        ChatCall call = new ChatCall(prompt.feature(), system, user, facts, schema, type, maxTokens, providerTools);

        long start = System.nanoTime();
        ChatOutcome outcome;
        try {
            outcome = client.chat(call);
        } catch (AiException e) {
            throw e;
        } catch (RuntimeException e) {
            recorder.record(new AiCallRecorder.Attempt(prompt.feature(), prompt.missionId(), kind, model, 0, 0,
                    elapsed(start), AiCall.Status.ERROR, attempt, e.getMessage(), hash));
            log.error("AI provider call failed for {}", prompt.feature(), e);
            throw AiException.providerError(prompt.feature(), firstLine(String.valueOf(e.getMessage())));
        }
        long latency = outcome.simulatedLatencyMs() != null ? outcome.simulatedLatencyMs() : elapsed(start);
        return new Exchange(prompt, kind, outcome, session.unmask(outcome.text()), latency, attempt, hash, traces);
    }

    private void guard(String feature, Long missionId, AiCall.Kind kind, String model, String hash) {
        if (!props.live()) {
            return;
        }
        if (spendGuard.budgetExhausted()) {
            recorder.record(new AiCallRecorder.Attempt(feature, missionId, kind, model, 0, 0, 0,
                    AiCall.Status.BUDGET_EXCEEDED, 1, "daily budget reached", hash));
            throw AiException.budgetExceeded(props.dailyBudgetUsd().toPlainString());
        }
        if (!spendGuard.tryAcquire(rateKey())) {
            recorder.record(new AiCallRecorder.Attempt(feature, missionId, kind, model, 0, 0, 0,
                    AiCall.Status.RATE_LIMITED, 1, "rate limit", hash));
            throw AiException.rateLimited();
        }
    }

    private String rateKey() {
        CurrentUser user = context.currentOptional().orElse(null);
        if (user == null) {
            return "system";
        }
        if (user.tenantId() != null) {
            return "tenant:" + user.tenantId();
        }
        return user.supplierId() != null ? "supplier:" + user.supplierId() : "user:" + user.userId();
    }

    /** Tool arguments are unmasked before the handler runs; results are masked before they go back to the model. */
    private ProviderTool providerTool(AiTool tool, PiiMasker.Session session, List<ToolCallTrace> traces) {
        return new ProviderTool(tool.name(), tool.description(), tool.inputSchema(), json -> {
            try {
                Map<String, Object> args = json == null || json.isBlank() ? Map.of()
                        : mapper.readValue(session.unmask(json), new TypeReference<Map<String, Object>>() { });
                Object result = tool.handler().apply(args);
                traces.add(new ToolCallTrace(tool.name(), args, result));
                return session.mask(mapper.writeValueAsString(result));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                traces.add(new ToolCallTrace(tool.name(), json, Map.of("error", "invalid arguments")));
                return "{\"error\":\"invalid arguments\"}";
            }
        });
    }

    private List<String> validate(Object value) {
        List<String> errors = new ArrayList<>();
        if (value == null) {
            errors.add("empty output");
            return errors;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(value);
        violations.forEach(v -> errors.add(v.getPropertyPath() + " " + v.getMessage()));
        if (value instanceof ValidatableOutput v) {
            errors.addAll(v.validationErrors());
        }
        return errors;
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String firstLine(String message) {
        String line = message.lines().findFirst().orElse(message);
        return line.length() > 300 ? line.substring(0, 300) : line;
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Exchange(AiPrompt prompt, AiCall.Kind kind, ChatOutcome outcome, String text, long latencyMs,
                            int attemptNo, String hash, List<ToolCallTrace> traces) {

        AiCallRecorder.Attempt attempt(AiCall.Status status, String error) {
            return new AiCallRecorder.Attempt(prompt.feature(), prompt.missionId(), kind, outcome.model(),
                    outcome.inputTokens(), outcome.outputTokens(), latencyMs, status, attemptNo, error, hash);
        }

        <T> AiResult<T> result(T value, AiCall call, List<ToolCallTrace> toolCalls) {
            BigDecimal cost = call.getEstimatedCostUsd();
            return new AiResult<>(value, outcome.model(), outcome.inputTokens(), outcome.outputTokens(), latencyMs, cost,
                    List.copyOf(toolCalls), call.getId());
        }
    }
}
