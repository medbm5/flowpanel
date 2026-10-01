package com.flowpanel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
}
