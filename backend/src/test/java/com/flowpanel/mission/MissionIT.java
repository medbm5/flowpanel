package com.flowpanel.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class MissionIT extends AbstractIntegrationTest {

    @Autowired
    ArtifactService artifacts;

    Cookie claire;

    @BeforeEach
    void setUp() throws Exception {
        claire = login("claire");
    }

    @Test
    void seededMissionsAreVisibleToTheirTenantOnly() throws Exception {
        JsonNode board = call(get("/missions").cookie(claire), status().isOk());
        assertThat(board.findValuesAsText("ref")).contains(MissionService.ref(142), MissionService.ref(147));
        assertThat(board.findValuesAsText("ref")).doesNotContain(MissionService.ref(145));

        long metalProMission = idOf(call(get("/missions").cookie(login("marc")), status().isOk()), MissionService.ref(145));
        call(get("/missions/" + metalProMission).cookie(claire), status().isNotFound());
    }

    @Test
    void seededMissionsArePreAdvanced() throws Exception {
        JsonNode board = call(get("/missions").cookie(claire), status().isOk());
        assertThat(phaseOf(board, MissionService.ref(142))).isEqualTo("TIMESHEETS");
        assertThat(phaseOf(board, MissionService.ref(147))).isEqualTo("SOURCING");
    }

    @Test
    void createFromTemplateOrEmail() throws Exception {
        JsonNode fromTemplate = create(Map.of("templateCode", "forklift-lille"));
        assertThat(fromTemplate.get("phase").asText()).isEqualTo("INTAKE");
        assertThat(fromTemplate.get("ref").asText()).startsWith("ORD-");
        assertThat(fromTemplate.get("title").asText()).isEqualTo("Caristes CACES 3 — Lille Lesquin");
        assertThat(fromTemplate.get("phases").get(0).get("state").asText()).isEqualTo("ACTIVE");
        assertThat(fromTemplate.get("phases").get(1).get("state").asText()).isEqualTo("LOCKED");

        JsonNode fromEmail = create(Map.of("emailText", "Objet : Besoin urgent\n\nBonjour, il nous faut 1 agent."));
        assertThat(fromEmail.get("title").asText()).isEqualTo("Besoin urgent");

        call(post("/missions").cookie(claire).contentType(MediaType.APPLICATION_JSON).content("{}"), status().isBadRequest());
    }

    @Test
    void finalizeIsRejectedWhenGateIsNotMetOrPhaseIsNotCurrent() throws Exception {
        long id = create(Map.of("templateCode", "office-paris")).get("id").asLong();

        JsonNode notReady = call(post("/missions/" + id + "/phases/intake/finalize").cookie(claire), status().isConflict());
        assertThat(notReady.get("unmetChecks")).isNotEmpty();

        JsonNode wrongPhase = call(post("/missions/" + id + "/phases/sourcing/finalize").cookie(claire), status().isConflict());
        assertThat(wrongPhase.get("currentPhase").asText()).isEqualTo("INTAKE");

        call(post("/missions/" + id + "/phases/bogus/finalize").cookie(claire), status().isBadRequest());
    }

    @Test
    void twoMissionsAdvanceIndependently() throws Exception {
        JsonNode a = create(Map.of("templateCode", "forklift-lille"));
        JsonNode b = create(Map.of("templateCode", "pickers-roubaix"));
        long idA = a.get("id").asLong();
        long idB = b.get("id").asLong();

        completeIntake(idA);
        JsonNode afterA = call(post("/missions/" + idA + "/phases/intake/finalize").cookie(claire), status().isOk());
        assertThat(afterA.get("phase").asText()).isEqualTo("SOURCING");
        assertThat(afterA.get("phases").get(0).get("state").asText()).isEqualTo("DONE");
        assertThat(afterA.get("phases").get(0).get("finalizedBy").asText()).isEqualTo("Claire Dubois");

        assertThat(call(get("/missions/" + idB).cookie(claire), status().isOk()).get("phase").asText()).isEqualTo("INTAKE");

        completeIntake(idB);
        call(post("/missions/" + idB + "/phases/intake/finalize").cookie(claire), status().isOk());
        completeSourcing(idB);
        JsonNode afterB = call(post("/missions/" + idB + "/phases/sourcing/finalize").cookie(claire), status().isOk());

        assertThat(afterB.get("phase").asText()).isEqualTo("CONTRACTS");
        assertThat(call(get("/missions/" + idA).cookie(claire), status().isOk()).get("phase").asText()).isEqualTo("SOURCING");

        JsonNode audit = call(get("/missions/" + idB + "/audit").cookie(claire), status().isOk());
        assertThat(audit.findValuesAsText("action")).contains("mission.created", "phase.finalized");
    }

    @Test
    void statusFilters() throws Exception {
        JsonNode open = call(get("/missions?status=open").cookie(claire), status().isOk());
        assertThat(open).isNotEmpty();
        open.forEach(m -> assertThat(m.get("phase").asText()).isNotEqualTo("CLOSED"));
        call(get("/missions?status=closed").cookie(claire), status().isOk());
        call(get("/missions?status=review").cookie(claire), status().isOk());
        call(get("/missions?status=weird").cookie(claire), status().isBadRequest());
    }

    @Test
    void templatesAreListed() throws Exception {
        JsonNode templates = call(get("/request-templates").cookie(claire), status().isOk());
        assertThat(templates.findValuesAsText("code")).containsExactly("forklift-lille", "office-paris", "pickers-roubaix");
    }

    /** Extracts the order and confirms every flagged field. */
    private void completeIntake(long id) throws Exception {
        JsonNode view = call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        for (JsonNode f : view.get("fields")) {
            if (f.get("needsReview").asBoolean()) {
                call(post("/missions/" + id + "/intake/fields/" + f.get("name").asText() + "/confirm").cookie(claire),
                        status().isOk());
            }
        }
    }

    /** Publishes and selects the top-ranked candidates until every position is filled. */
    private void completeSourcing(long id) throws Exception {
        JsonNode view = call(post("/missions/" + id + "/sourcing/publish").cookie(claire), status().isOk());
        int quantity = view.get("quantity").asInt();
        for (int i = 0; i < quantity; i++) {
            long candidate = view.get("candidates").get(i).get("candidateId").asLong();
            call(post("/missions/" + id + "/sourcing/select/" + candidate).cookie(claire), status().isOk());
        }
    }

    private JsonNode create(Map<String, String> body) throws Exception {
        return call(post("/missions").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), status().isCreated());
    }

    static long idOf(JsonNode board, String ref) {
        for (JsonNode n : board) {
            if (n.get("ref").asText().equals(ref)) {
                return n.get("id").asLong();
            }
        }
        throw new AssertionError("Mission " + ref + " not on board");
    }

    static String phaseOf(JsonNode board, String ref) {
        for (JsonNode n : board) {
            if (n.get("ref").asText().equals(ref)) {
                return n.get("phase").asText();
            }
        }
        throw new AssertionError("Mission " + ref + " not on board");
    }
}
