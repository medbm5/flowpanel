package com.flowpanel.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.live.SpringAiModelClient;
import com.flowpanel.ai.mock.MockAiModelClient;
import com.flowpanel.ai.mock.MockResponder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /** {@code AI_PROFILE=live} → OpenAI through Spring AI; anything else → deterministic mock (default). */
    @Bean
    AiModelClient aiModelClient(AiProperties props, ObjectProvider<MockResponder> responderProvider, ObjectMapper mapper) {
        if (props.live()) {
            log.info("AI profile: live (chat={}, embeddings={})", props.chatModel(), props.embeddingModel());
            var mock = new MockAiModelClient(responderProvider.orderedStream().toList(), props.chatModel(),
                    props.embeddingModel(), mapper);
            return new RoutingModelClient(new SpringAiModelClient(props.apiKey(), props.chatModel(), props.embeddingModel()), mock);
        }
        var responders = responderProvider.orderedStream().toList();
        log.info("AI profile: mock ({} responders)", responders.size());
        return new MockAiModelClient(responders, props.chatModel(), props.embeddingModel(), mapper);
    }
}
