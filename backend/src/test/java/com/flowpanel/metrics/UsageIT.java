package com.flowpanel.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class UsageIT extends AbstractIntegrationTest {

    static final long CLAIRE = 1;
    static final long THOMAS = 4;

    @Test
    void aiCallsAreAttributedToTheUserWhoTriggeredThem() throws Exception {
        Cookie admin = login("admin");
        long before = callsOf(call(get("/admin/usage/users?days=1").cookie(admin), status().isOk()), THOMAS);

        call(post("/copilot/ask").cookie(login("thomas")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What are my hourly rates?\"}"), status().isOk());

        JsonNode overview = call(get("/admin/usage/users?days=1").cookie(admin), status().isOk());
        assertThat(callsOf(overview, THOMAS)).isEqualTo(before + 1);
        assertThat(overview.get("days").asInt()).isEqualTo(1);
        assertThat(overview.get("config").get("chatModel").asText()).isEqualTo("gpt-4o-mini");
        assertThat(overview.get("config").get("pricing")).isNotEmpty();
        assertThat(overview.get("users").findValuesAsText("persona")).contains("claire", "marc", "nadia", "thomas", "admin");
        assertThat(overview.get("totals").get("calls").asLong()).isPositive();

        double shares = 0;
        for (JsonNode u : overview.get("users")) {
            shares += u.get("shareOfCost").asDouble();
        }
        assertThat(shares).isBetween(0.99, 1.01);
    }

    @Test
    void userDetailBreaksUsageDownByFeatureModelStatusAndDay() throws Exception {
        Cookie admin = login("admin");
        JsonNode detail = call(get("/admin/usage/detail?userId=" + CLAIRE + "&days=7").cookie(admin), status().isOk());
        assertThat(detail.get("user").get("displayName").asText()).isEqualTo("Claire Dubois");
        assertThat(detail.get("user").get("calls").asLong()).isPositive();
        assertThat(detail.get("byFeature").findValuesAsText("key")).contains("intake.extract");
        assertThat(detail.get("byModel").findValuesAsText("key")).contains("mock/gpt-4o-mini");
        assertThat(detail.get("byStatus").findValuesAsText("key")).contains("OK");
        assertThat(detail.get("daily")).hasSize(7);
        assertThat(detail.get("recentCalls")).isNotEmpty();
        assertThat(new BigDecimal(detail.get("avgCostPerCallUsd").asText())).isPositive();
    }

    @Test
    void usageIsAdminOnlyAndValidatesTheUser() throws Exception {
        call(get("/admin/usage/users").cookie(login("claire")), status().isForbidden());
        call(get("/admin/usage/detail?userId=9999").cookie(login("admin")), status().isNotFound());
    }

    static long callsOf(JsonNode overview, long userId) {
        for (JsonNode u : overview.get("users")) {
            if (!u.get("userId").isNull() && u.get("userId").asLong() == userId) {
                return u.get("calls").asLong();
            }
        }
        throw new AssertionError("user " + userId + " missing");
    }
}
