package com.flowpanel.ai.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.AiModelClient;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mock profile provider: deterministic outputs keyed by prompt template id, simulated tokens and latency. */
public class MockAiModelClient implements AiModelClient {

    private static final int HISTORY = 200;

    private final Map<String, MockResponder> responders = new HashMap<>();
    private final String chatModel;
    private final String embeddingModel;
    private final ObjectMapper mapper;
    private final Deque<ChatCall> history = new ArrayDeque<>();
    private final Deque<List<String>> embeddingHistory = new ArrayDeque<>();

    public MockAiModelClient(List<MockResponder> responders, String chatModel, String embeddingModel, ObjectMapper mapper) {
        for (MockResponder r : responders) {
            r.features().forEach(f -> this.responders.put(f, r));
        }
        this.chatModel = "mock/" + chatModel;
        this.embeddingModel = "mock/" + embeddingModel;
        this.mapper = mapper;
    }

    @Override
    public String profile() {
        return "mock";
    }

    @Override
    public String chatModel() {
        return chatModel;
    }

    @Override
    public String embeddingModel() {
        return embeddingModel;
    }

    @Override
    public ChatOutcome chat(ChatCall call) {
        remember(history, call);
        MockResponder responder = responders.get(call.feature());
        String text;
        if (responder != null) {
            text = responder.respond(call, toolRunner(call));
        } else if (call.jsonSchema() == null) {
            text = "[mock] " + call.feature() + " response.";
        } else {
            throw new IllegalStateException("No mock responder for structured feature " + call.feature());
        }
        int input = estimateTokens(call.system()) + estimateTokens(call.user()) + estimateTokens(json(call.facts()));
        int output = estimateTokens(text);
        return new ChatOutcome(text, chatModel, input, output, 180L + output * 6L);
    }

    @Override
    public EmbeddingOutcome embed(List<String> texts) {
        remember(embeddingHistory, List.copyOf(texts));
        List<float[]> vectors = texts.stream().map(MockEmbeddings::embed).toList();
        int tokens = texts.stream().mapToInt(MockAiModelClient::estimateTokens).sum();
        return new EmbeddingOutcome(vectors, embeddingModel, tokens, 40L + texts.size() * 5L);
    }

    /** Calls as received by the "provider" (already masked), newest last. Used by tests to assert no PII leaks. */
    public synchronized List<ChatCall> recentCalls() {
        return List.copyOf(history);
    }

    public synchronized List<List<String>> recentEmbeddingInputs() {
        return List.copyOf(embeddingHistory);
    }

    private MockResponder.ToolRunner toolRunner(ChatCall call) {
        return new MockResponder.ToolRunner() {
            @Override
            public Optional<ProviderTool> find(String name) {
                return call.tools().stream().filter(t -> t.name().equals(name)).findFirst();
            }

            @Override
            public String call(String name, Map<String, Object> arguments) {
                ProviderTool tool = find(name).orElseThrow(() -> new IllegalArgumentException("Unknown tool " + name));
                return tool.invoke().apply(json(arguments));
            }
        };
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private synchronized <T> void remember(Deque<T> deque, T item) {
        deque.addLast(item);
        while (deque.size() > HISTORY) {
            deque.pollFirst();
        }
    }

    static int estimateTokens(String text) {
        return text == null ? 0 : (text.length() + 3) / 4;
    }
}
