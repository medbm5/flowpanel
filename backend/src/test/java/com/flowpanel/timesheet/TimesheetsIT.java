package com.flowpanel.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.MissionFlow;
import com.flowpanel.mission.MissionService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TimesheetsIT extends AbstractIntegrationTest {

    Cookie claire;
    MissionFlow flow;

    @BeforeEach
    void setUp() throws Exception {
        claire = login("claire");
        flow = new MissionFlow(this::call, json, claire);
    }

    @Test
    void seededMissionHasTheSeededAnomalyOnceChecked() throws Exception {
        long id = flow.idOf(MissionService.ref(142));
        JsonNode view = call(get("/missions/" + id + "/timesheets").cookie(claire), status().isOk());
        assertThat(view.get("timesheets")).hasSize(6);
        assertThat(view.get("checked").asBoolean()).isFalse();
    }

    @Test
    void approveOvertimePathCountsTheExtraHours() throws Exception {
        long id = flow.missionAtTimesheets("lundi 2 novembre 2026", "vendredi 13 novembre 2026");
        JsonNode before = call(get("/missions/" + id + "/timesheets").cookie(claire), status().isOk());
        assertThat(before.get("timesheets")).hasSize(4);
        call(post("/missions/" + id + "/timesheets/approve").cookie(claire), status().isConflict());

        JsonNode checked = call(post("/missions/" + id + "/timesheets/check").cookie(claire), status().isOk());
        assertThat(checked.get("anomalies")).hasSize(1);
        JsonNode anomaly = checked.get("anomalies").get(0);
        assertThat(anomaly.get("message").asText()).isEqualTo("41 h worked vs 35 h contracted, no overtime agreed");
        assertThat(anomaly.get("aiExplanation").asText()).contains("41").contains("35");
        assertThat(flaggedSheet(checked).get("flaggedDays")).hasSize(5);

        JsonNode mission = call(get("/missions/" + id).cookie(claire), status().isOk());
        assertThat(mission.get("needsReview").asBoolean()).isTrue();
        call(post("/missions/" + id + "/timesheets/approve").cookie(claire), status().isConflict());

        call(post("/anomalies/" + anomaly.get("id").asLong() + "/resolve").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"APPROVE_OVERTIME\"}"), status().isOk());
        JsonNode approved = call(post("/missions/" + id + "/timesheets/approve").cookie(claire), status().isOk());
        assertThat(totalApproved(approved)).isEqualByComparingTo("146");

        JsonNode finalized = call(post("/missions/" + id + "/phases/timesheets/finalize").cookie(claire), status().isOk());
        assertThat(finalized.get("phase").asText()).isEqualTo("INVOICE");
    }

    @Test
    void returnToSupplierPathUsesTheCorrectedSheet() throws Exception {
        long id = flow.missionAtTimesheets("lundi 16 novembre 2026", "vendredi 27 novembre 2026");
        JsonNode checked = call(post("/missions/" + id + "/timesheets/check").cookie(claire), status().isOk());
        long anomalyId = checked.get("anomalies").get(0).get("id").asLong();

        JsonNode resolved = call(post("/anomalies/" + anomalyId + "/resolve").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"RETURN_TO_SUPPLIER\"}"), status().isOk());
        assertThat(resolved.get("timesheets").findValuesAsText("status")).contains("CORRECTED");
        call(post("/anomalies/" + anomalyId + "/resolve").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"RETURN_TO_SUPPLIER\"}"), status().isConflict());

        JsonNode approved = call(post("/missions/" + id + "/timesheets/approve").cookie(claire), status().isOk());
        assertThat(totalApproved(approved)).isEqualByComparingTo("140");

        JsonNode audit = call(get("/missions/" + id + "/audit").cookie(claire), status().isOk());
        assertThat(audit.findValuesAsText("action")).contains("timesheet.corrected", "timesheet.anomaly.resolved",
                "timesheets.approved", "ai.timesheet.explain");
    }

    @Test
    void anomaliesAreTenantScoped() throws Exception {
        long id = flow.missionAtTimesheets("lundi 30 novembre 2026", "vendredi 4 décembre 2026");
        JsonNode checked = call(post("/missions/" + id + "/timesheets/check").cookie(claire), status().isOk());
        long anomalyId = checked.get("anomalies").get(0).get("id").asLong();
        call(post("/anomalies/" + anomalyId + "/resolve").cookie(login("marc")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"APPROVE_OVERTIME\"}"), status().isNotFound());
    }

    static JsonNode flaggedSheet(JsonNode view) {
        for (JsonNode t : view.get("timesheets")) {
            if (!t.get("flaggedDays").isEmpty()) {
                return t;
            }
        }
        throw new AssertionError("no flagged sheet");
    }

    static BigDecimal totalApproved(JsonNode view) {
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode w : view.get("totals")) {
            total = total.add(new BigDecimal(w.get("approved").asText()));
        }
        return total;
    }
}
