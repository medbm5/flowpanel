package com.flowpanel.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void renderStyleUrlBecomesJdbcWithCredentials() {
        var props = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgres://flowpanel:s3cr%40t@dpg-abc123-a.frankfurt-postgres.render.com:5432/flowpanel");
        assertThat(props).containsEntry("spring.datasource.url",
                "jdbc:postgresql://dpg-abc123-a.frankfurt-postgres.render.com:5432/flowpanel");
        assertThat(props).containsEntry("spring.datasource.username", "flowpanel");
        assertThat(props).containsEntry("spring.datasource.password", "s3cr@t");
    }

    @Test
    void neonStyleUrlKeepsQueryParameters() {
        var props = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgresql://user:pw@ep-cool-1234-pooler.eu-central-1.aws.neon.tech/neondb?sslmode=require");
        assertThat(props.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://ep-cool-1234-pooler.eu-central-1.aws.neon.tech/neondb?sslmode=require");
    }

    @Test
    void jdbcUrlsAreLeftAlone() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.convert("jdbc:postgresql://localhost:5433/flowpanel")).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.convert(null)).isEmpty();
    }
}
