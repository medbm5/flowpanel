package com.flowpanel.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.ai.AiModelClient;
import com.flowpanel.ai.mock.MockAiModelClient;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class IntakeIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AiModelClient client;

    Cookie claire;

    @BeforeEach
    void setUp() throws Exception {
        claire = login("claire");
    }

    @Test
    void validExtractionFlagsTheLowConfidenceField() throws Exception {
        long id = create(Map.of("templateCode", "forklift-lille"));
        JsonNode view = call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        assertThat(view.get("extracted").asBoolean()).isTrue();
        assertThat(field(view, "position").get("value").asText()).isEqualTo("Cariste");
        assertThat(field(view, "quantity").get("value").asText()).isEqualTo("2");
        assertThat(view.get("needsReviewCount").asInt()).isEqualTo(1);
        assertThat(field(view, "endDate").get("needsReview").asBoolean()).isTrue();

        JsonNode mission = call(get("/missions/" + id).cookie(claire), status().isOk());
        assertThat(mission.get("needsReview").asBoolean()).isTrue();
        assertThat(mission.get("nextAction").asText()).isEqualTo("Review 1 flagged field");
        assertThat(mission.get("artifacts").get(0).get("type").asText()).isEqualTo("ORDER");
        assertThat(mission.get("artifacts").get(0).get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void gateFailsUntilFlaggedFieldsAreReviewedThenPasses() throws Exception {
        long id = create(Map.of("templateCode", "forklift-lille"));
        JsonNode beforeExtract = call(post("/missions/" + id + "/phases/intake/finalize").cookie(claire), status().isConflict());
        assertThat(beforeExtract.get("unmetChecks").toString()).contains("Order extracted from the email");

        call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        JsonNode unreviewed = call(post("/missions/" + id + "/phases/intake/finalize").cookie(claire), status().isConflict());
        assertThat(unreviewed.get("unmetChecks").toString()).contains("1 field(s) to review");

        JsonNode corrected = call(post("/missions/" + id + "/intake/fields/endDate/confirm").cookie(claire)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"2026-10-23\"}"), status().isOk());
        assertThat(field(corrected, "endDate").get("value").asText()).isEqualTo("2026-10-23");
        assertThat(field(corrected, "endDate").get("corrected").asBoolean()).isTrue();
        assertThat(field(corrected, "endDate").get("aiValue").asText()).isEqualTo("2026-10-16");
        assertThat(corrected.get("needsReviewCount").asInt()).isZero();

        JsonNode finalized = call(post("/missions/" + id + "/phases/intake/finalize").cookie(claire), status().isOk());
        assertThat(finalized.get("phase").asText()).isEqualTo("SOURCING");
        assertThat(finalized.get("artifacts").get(0).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(finalized.get("artifacts").get(0).get("payload").get("endDate").asText()).isEqualTo("2026-10-23");

        JsonNode audit = call(get("/missions/" + id + "/audit").cookie(claire), status().isOk());
        assertThat(audit.findValuesAsText("action")).contains("ai.intake.extract", "intake.extracted",
                "intake.field.corrected", "phase.finalized");

        // finalized phase is read-only
        call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isConflict());
        assertThat(call(get("/missions/" + id + "/intake").cookie(claire), status().isOk()).get("readOnly").asBoolean()).isTrue();
    }

    @Test
    void invalidCorrectionIsRejected() throws Exception {
        long id = create(Map.of("templateCode", "pickers-roubaix"));
        call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        JsonNode problem = call(post("/missions/" + id + "/intake/fields/hourlyRate/confirm").cookie(claire)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"-1\"}"), status().isBadRequest());
        assertThat(problem.get("field").asText()).isEqualTo("hourlyRate");
        call(post("/missions/" + id + "/intake/fields/nope/confirm").cookie(claire), status().isNotFound());

        JsonNode saved = call(post("/missions/" + id + "/intake/fields/hourlyRate/confirm").cookie(claire)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"12,50 €\"}"), status().isOk());
        assertThat(field(saved, "hourlyRate").get("value").asText()).isEqualTo("12.50");
        assertThat(field(saved, "hourlyRate").get("needsReview").asBoolean()).isFalse();
    }

    @Test
    void invalidStructuredOutputIsRetriedOnce() throws Exception {
        String email = jdbc.queryForObject("select email_text from request_template where code = 'forklift-lille'", String.class)
                + "\n#mock-invalid-once";
        long id = create(Map.of("emailText", email));
        JsonNode view = call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        assertThat(field(view, "position").get("value").asText()).isEqualTo("Cariste");
        List<String> statuses = jdbc.queryForList("select status from ai_call where mission_id = ? order by id", String.class, id);
        assertThat(statuses).containsExactly("INVALID_OUTPUT", "OK");
    }

    @Test
    void repeatedInvalidOutputFailsGracefully() throws Exception {
        long id = create(Map.of("emailText", "Objet : test\nBesoin de 2 caristes #mock-invalid-always"));
        JsonNode problem = call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isBadGateway());
        assertThat(problem.get("code").asText()).isEqualTo("ai_invalid_output");
        JsonNode view = call(get("/missions/" + id + "/intake").cookie(claire), status().isOk());
        assertThat(view.get("extracted").asBoolean()).isFalse();
        assertThat(jdbc.queryForList("select status from ai_call where mission_id = ?", String.class, id))
                .containsExactly("INVALID_OUTPUT", "INVALID_OUTPUT");
    }

    @Test
    void replacedEmployeeIsMaskedForTheProviderAndRestored() throws Exception {
        long id = create(Map.of("templateCode", "office-paris"));
        JsonNode view = call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        assertThat(field(view, "replacedEmployee").get("value").asText()).isEqualTo("Julie Perrin");
        assertThat(field(view, "legalReason").get("value").asText()).isEqualTo("REPLACEMENT");
        AiModelClient.ChatCall sent = ((MockAiModelClient) client).recentCalls().getLast();
        assertThat(sent.user()).doesNotContain("Julie Perrin", "Nathalie Roche", "nathalie.roche@", "01 44 55 66 77");
    }

    @Test
    void rawEmailMissionIsRenamedFromTheExtraction() throws Exception {
        long id = create(Map.of("emailText", "Bonjour,\nNous avons besoin de 4 agents de quai sur l'entrepôt Lille Lesquin, "
                + "du lundi 9 novembre 2026 au vendredi 20 novembre 2026, du lundi au vendredi 5h00-12h00 (35 h hebdo).\n"
                + "Taux horaire : 12,80 €.\nMotif : accroissement temporaire d'activité."));
        call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        JsonNode mission = call(get("/missions/" + id).cookie(claire), status().isOk());
        assertThat(mission.get("title").asText()).isEqualTo("Agent de quai — Entrepôt Lille Lesquin");
        assertThat(mission.get("site").asText()).isEqualTo("Entrepôt Lille Lesquin");
    }

    @Test
    void extractionIsTenantScopedAndPhaseGuarded() throws Exception {
        long seededSourcing = idOfRef(com.flowpanel.mission.MissionService.ref(147));
        call(post("/missions/" + seededSourcing + "/intake/extract").cookie(claire), status().isConflict());
        call(post("/missions/" + seededSourcing + "/intake/extract").cookie(login("marc")), status().isNotFound());
    }

    @Test
    void previewExtractsWithoutAMission() throws Exception {
        JsonNode preview = call(post("/intake/preview").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailText\":\"Nous recherchons 2 caristes du 3 novembre 2026 au 13 novembre 2026. Taux horaire : 13 €.\"}"),
                status().isOk());
        assertThat(preview.get("fields").findValuesAsText("name")).contains("position", "quantity");
    }

    private long idOfRef(String ref) throws Exception {
        for (JsonNode m : call(get("/missions").cookie(claire), status().isOk())) {
            if (m.get("ref").asText().equals(ref)) {
                return m.get("id").asLong();
            }
        }
        throw new AssertionError(ref);
    }

    private long create(Map<String, String> body) throws Exception {
        return call(post("/missions").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), status().isCreated()).get("id").asLong();
    }

    static JsonNode field(JsonNode view, String name) {
        for (JsonNode f : view.get("fields")) {
            if (f.get("name").asText().equals(name)) {
                return f;
            }
        }
        throw new AssertionError("no field " + name);
    }
}
