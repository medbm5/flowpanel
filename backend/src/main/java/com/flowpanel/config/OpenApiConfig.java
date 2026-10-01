package com.flowpanel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI flowpanelOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Flowpanel API")
                .version("0.1.0")
                .description("Mini VMS for temporary staffing. Demo project, synthetic data only."));
    }

    /**
     * Response DTOs are Java records that always serialize every component, so every property is marked required
     * (values may still be null). This keeps the generated TypeScript types strict.
     */
    @Bean
    OpenApiCustomizer requiredRecordProperties() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            for (Map.Entry<String, Schema> entry : openApi.getComponents().getSchemas().entrySet()) {
                Schema<?> schema = entry.getValue();
                if (schema.getProperties() != null && !schema.getProperties().isEmpty()
                        && !entry.getKey().endsWith("Request") && !entry.getKey().equals("ProblemDetail")) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            }
        };
    }
}
