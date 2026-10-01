package com.flowpanel.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.MissionFlow;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AdminMetricsIT extends AbstractIntegrationTest {

    @Test
    void dashboardShowsRealNumbersFromAiCallsAfterRunningAMission() throws Exception {
        Cookie claire = login("claire");
        new MissionFlow(this::call, json, claire).missionAtSourcing("lundi 5 octobre 2026", "vendredi 9 octobre 2026");

        Cookie admin = login("admin");
        JsonNode overview = call(get("/admin/metrics/overview").cookie(admin), status().isOk());
        JsonNode summary = overview.get("summary");
        assertThat(summary.get("calls").asLong()).isPositive();
        assertThat(summary.get("tokens").asLong()).isPositive();
        assertThat(summary.get("p50LatencyMs").asLong()).isPositive();
        assertThat(summary.get("p95LatencyMs").asLong()).isGreaterThanOrEqualTo(summary.get("p50LatencyMs").asLong());
        assertThat(summary.get("profile").asText()).isEqualTo("mock");
        assertThat(overview.get("byTenant").findValuesAsText("tenant")).contains("LogiNord");
        assertThat(overview.get("byFeature").findValuesAsText("feature")).contains("intake.extract");
        assertThat(overview.get("corrections").get("aiFields").asLong()).isPositive();
    }

    @Test
    void evalRunsAreRecordedAndListed() throws Exception {
        Cookie admin = login("admin");
        call(post("/admin/evals/runs").cookie(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"gitSha":"abc1234","profile":"mock","passed":true,"metrics":{"intake.field_accuracy":0.95},"thresholds":{"intake.field_accuracy":0.9}}
                """), status().isCreated());
        JsonNode runs = call(get("/admin/metrics/evals").cookie(admin), status().isOk());
        assertThat(runs.get(0).get("gitSha").asText()).isEqualTo("abc1234");
        assertThat(runs.get(0).get("metrics").get("intake.field_accuracy").asDouble()).isEqualTo(0.95);
    }

    @Test
    void auditExplorerFiltersByKindAndIsAdminOnly() throws Exception {
        Cookie admin = login("admin");
        JsonNode ai = call(get("/admin/audit?kind=AI&limit=20").cookie(admin), status().isOk());
        ai.forEach(e -> assertThat(e.get("actorKind").asText()).isEqualTo("AI"));
        call(get("/admin/metrics/overview").cookie(login("claire")), status().isForbidden());
        call(get("/admin/audit").cookie(login("nadia")), status().isForbidden());
    }
}
