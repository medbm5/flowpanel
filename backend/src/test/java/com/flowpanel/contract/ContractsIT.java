package com.flowpanel.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.ai.AiModelClient;
import com.flowpanel.ai.mock.MockAiModelClient;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ContractsIT extends AbstractIntegrationTest {

    @Autowired
    AiModelClient client;

    Cookie claire;

    @BeforeEach
    void setUp() throws Exception {
        claire = login("claire");
    }

    @Test
    void generateFixSignAndFinalize() throws Exception {
        long id = missionAtContracts("lundi 30 novembre 2026", "vendredi 4 décembre 2026");

        call(post("/missions/" + id + "/contracts/sign").cookie(claire), status().isConflict());

        JsonNode view = call(post("/missions/" + id + "/contracts/generate").cookie(claire), status().isOk());
        assertThat(view.get("contracts")).hasSize(2);
        JsonNode first = view.get("contracts").get(0);
        assertThat(first.get("ref").asText()).matches("CT-\\d{4}-01");
        assertThat(first.get("blocking").asBoolean()).isTrue();
        assertThat(failing(first)).isEqualTo(ContractRulesEngine.DATES_WITHIN_ORDER);
        assertThat(first.get("endDate").asText()).isEqualTo("2026-12-11");
        assertThat(view.get("contracts").get(1).get("blocking").asBoolean()).isFalse();

        String worker = first.get("workerName").asText();
        assertThat(first.get("aiDrafted").asBoolean()).isTrue();
        assertThat(first.get("content").asText()).contains(worker).contains("13.20");
        assertThat(((MockAiModelClient) client).recentCalls().getLast().user()).doesNotContain(worker);

        JsonNode mission = call(get("/missions/" + id).cookie(claire), status().isOk());
        assertThat(mission.get("needsReview").asBoolean()).isTrue();
        assertThat(mission.get("nextAction").asText()).isEqualTo("Fix the blocking compliance issue");

        JsonNode blocked = call(post("/missions/" + id + "/contracts/sign").cookie(claire), status().isConflict());
        assertThat(blocked.get("detail").asText()).contains(first.get("ref").asText());

        long contractId = first.get("id").asLong();
        call(post("/contracts/" + contractId + "/fix/" + ContractRulesEngine.RATE_MATCHES_ORDER).cookie(claire), status().isConflict());
        JsonNode fixed = call(post("/contracts/" + contractId + "/fix/" + ContractRulesEngine.DATES_WITHIN_ORDER).cookie(claire),
                status().isOk());
        assertThat(fixed.get("endDate").asText()).isEqualTo("2026-12-04");
        assertThat(fixed.get("blocking").asBoolean()).isFalse();

        call(post("/missions/" + id + "/phases/contracts/finalize").cookie(claire), status().isConflict());
        JsonNode signed = call(post("/missions/" + id + "/contracts/sign").cookie(claire), status().isOk());
        signed.get("contracts").forEach(c -> {
            assertThat(c.get("status").asText()).isEqualTo("SIGNED");
            assertThat(c.get("signedByClientAt").isNull()).isFalse();
            assertThat(c.get("signedBySupplierAt").isNull()).isFalse();
        });

        JsonNode finalized = call(post("/missions/" + id + "/phases/contracts/finalize").cookie(claire), status().isOk());
        assertThat(finalized.get("phase").asText()).isEqualTo("TIMESHEETS");
        assertThat(finalized.get("artifacts").findValuesAsText("type")).contains("CONTRACT");

        JsonNode audit = call(get("/missions/" + id + "/audit").cookie(claire), status().isOk());
        assertThat(audit.findValuesAsText("action")).contains("contracts.generated", "contract.fixed", "contracts.signed",
                "ai.contract.draft");
    }

    @Test
    void contractsCannotBeGeneratedTwiceOrFixedFromAnotherTenant() throws Exception {
        long id = missionAtContracts("lundi 7 décembre 2026", "vendredi 11 décembre 2026");
        JsonNode view = call(post("/missions/" + id + "/contracts/generate").cookie(claire), status().isOk());
        call(post("/missions/" + id + "/contracts/generate").cookie(claire), status().isConflict());
        long contractId = view.get("contracts").get(0).get("id").asLong();
        call(post("/contracts/" + contractId + "/fix/" + ContractRulesEngine.DATES_WITHIN_ORDER).cookie(login("marc")),
                status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    static String forklift(String from, String to) {
        return "Objet : Caristes Lesquin\n\nBonjour,\nNous avons besoin de 2 caristes CACES R489 cat. 3 sur l'entrepôt Lille Lesquin, "
                + "du " + from + " au " + to + ".\nHoraires : du lundi au vendredi, 6h00-13h00, soit 35 h par semaine. "
                + "Pas d'heures supplémentaires.\nTaux horaire : 13,20 € brut.\nMotif : accroissement temporaire d'activité.";
    }

    long missionAtContracts(String from, String to) throws Exception {
        long id = call(post("/missions").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("emailText", forklift(from, to)))), status().isCreated()).get("id").asLong();
        JsonNode intake = call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        for (JsonNode f : intake.get("fields")) {
            if (f.get("needsReview").asBoolean()) {
                call(post("/missions/" + id + "/intake/fields/" + f.get("name").asText() + "/confirm").cookie(claire), status().isOk());
            }
        }
        call(post("/missions/" + id + "/phases/intake/finalize").cookie(claire), status().isOk());
        JsonNode sourcing = call(post("/missions/" + id + "/sourcing/publish").cookie(claire), status().isOk());
        for (int i = 0; i < 2; i++) {
            call(post("/missions/" + id + "/sourcing/select/" + sourcing.get("candidates").get(i).get("candidateId").asLong())
                    .cookie(claire), status().isOk());
        }
        call(post("/missions/" + id + "/phases/sourcing/finalize").cookie(claire), status().isOk());
        return id;
    }

    static String failing(JsonNode contract) {
        for (JsonNode r : contract.get("checks")) {
            if (!r.get("passed").asBoolean()) {
                return r.get("ruleId").asText();
            }
        }
        return null;
    }
}
