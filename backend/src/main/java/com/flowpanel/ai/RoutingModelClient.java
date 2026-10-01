package com.flowpanel.ai;

import java.util.List;

/** Live client that hands chat calls to the mock while {@link AiProviderOverride} is active (demo seeding). */
public class RoutingModelClient implements AiModelClient {

    private final AiModelClient live;
    private final AiModelClient mock;

    public RoutingModelClient(AiModelClient live, AiModelClient mock) {
        this.live = live;
        this.mock = mock;
    }

    private AiModelClient chatClient() {
        return AiProviderOverride.mockChat() ? mock : live;
    }

    @Override
    public String profile() {
        return live.profile();
    }

    @Override
    public String chatModel() {
        return chatClient().chatModel();
    }

    @Override
    public String embeddingModel() {
        return live.embeddingModel();
    }

    @Override
    public ChatOutcome chat(ChatCall call) {
        return chatClient().chat(call);
    }

    @Override
    public EmbeddingOutcome embed(List<String> texts) {
        return live.embed(texts);
    }
}
