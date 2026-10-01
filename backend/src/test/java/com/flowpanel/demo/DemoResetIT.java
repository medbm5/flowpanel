package com.flowpanel.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.mission.MissionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DemoResetIT extends AbstractIntegrationTest {

    @Test
    void adminResetRemovesUserMissionsAndReseeds() throws Exception {
        var claire = login("claire");
        call(post("/missions").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateCode\":\"office-paris\"}"), status().isCreated());

        var nadia = login("nadia");
        call(post("/supplier/workers").cookie(nadia).contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Temp","lastName":"Worker","email":"temp@mail.example","phone":"06 00 00 00 01","city":"Lille",
                 "skills":["cariste"],"certifications":[],"experienceYears":1,"availableFrom":"2026-09-01"}
                """), status().isCreated());

        JsonNode result = call(post("/admin/demo/reset").cookie(login("admin")), status().isOk());
        assertThat(result.get("missions").asInt()).isEqualTo(3);

        JsonNode board = call(get("/missions").cookie(claire), status().isOk());
        assertThat(board.findValuesAsText("ref")).containsExactlyInAnyOrder(MissionService.ref(142), MissionService.ref(147));
        JsonNode pool = call(get("/supplier/workers").cookie(nadia), status().isOk());
        assertThat(pool.get("workers").findValuesAsText("lastName")).doesNotContain("Worker").contains("Haddad");
    }
}
