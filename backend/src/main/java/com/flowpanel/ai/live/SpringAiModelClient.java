package com.flowpanel.ai.live;

import com.flowpanel.ai.AiModelClient;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Live profile provider: OpenAI chat (structured outputs with JSON schema, function calling) and embeddings through
 * Spring AI. Receives masked content only. Model names come from configuration.
 */
public class SpringAiModelClient implements AiModelClient {

    private final OpenAiChatModel chat;
    private final OpenAiEmbeddingModel embeddings;
    private final String chatModel;
    private final String embeddingModel;

    public SpringAiModelClient(String apiKey, String chatModel, String embeddingModel) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_PROFILE=live requires OPENAI_API_KEY");
        }
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).build();
        this.chat = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(chatModel).temperature(0.1).build())
                .build();
        this.embeddings = new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(embeddingModel).build());
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String profile() {
        return "live";
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
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(chatModel)
                .temperature(0.1)
                .maxTokens(call.maxTokens());
        if (call.jsonSchema() != null) {
            options.responseFormat(ResponseFormat.builder()
                    .type(ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(StrictSchema.sanitize(call.jsonSchema()))
                    .build());
        }
        if (!call.tools().isEmpty()) {
            options.toolCallbacks(call.tools().stream().map(SpringAiModelClient::toCallback).toList());
        }
        List<Message> messages = List.of(new SystemMessage(call.system()), new UserMessage(call.user()));
        ChatResponse response = chat.call(new Prompt(messages, options.build()));
        String text = response.getResult() == null || response.getResult().getOutput() == null ? ""
                : response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();
        return new ChatOutcome(text == null ? "" : text, chatModel, intOf(usage == null ? null : usage.getPromptTokens()),
                intOf(usage == null ? null : usage.getCompletionTokens()), null);
    }

    @Override
    public EmbeddingOutcome embed(List<String> texts) {
        EmbeddingResponse response = embeddings.call(new EmbeddingRequest(texts,
                OpenAiEmbeddingOptions.builder().model(embeddingModel).build()));
        List<float[]> vectors = response.getResults().stream().map(r -> r.getOutput()).toList();
        Usage usage = response.getMetadata().getUsage();
        return new EmbeddingOutcome(vectors, embeddingModel, intOf(usage == null ? null : usage.getPromptTokens()), null);
    }

    private static ToolCallback toCallback(ProviderTool tool) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return tool.invoke().apply(toolInput);
            }
        };
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }
}
