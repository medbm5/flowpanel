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

        JsonNode result = call(post("/admin/demo/reset").cookie(login("admin")), status().isOk());
        assertThat(result.get("missions").asInt()).isEqualTo(3);

        JsonNode board = call(get("/missions").cookie(claire), status().isOk());
        assertThat(board.findValuesAsText("ref")).containsExactlyInAnyOrder(MissionService.ref(142), MissionService.ref(147));
    }
}
