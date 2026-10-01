package com.flowpanel.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.MissionFlow;
import com.flowpanel.mission.MissionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SupplierPortalIT extends AbstractIntegrationTest {

    static final String INTERSUD = "InterSud Intérim";

    static String workerJson(String first, String last) {
        return """
                {"firstName":"%s","lastName":"%s","email":"%s@mail.example","phone":"06 00 00 00 00","city":"Roubaix",
                 "skills":["préparation de commandes","scanner"],"certifications":["CACES R489 cat. 1"],"experienceYears":4,
                 "availableFrom":"2026-09-01","availableTo":"2026-12-31",
                 "profile":"Préparateur de commandes, picking au scanner, emballage."}
                """.formatted(first, last, first.toLowerCase());
    }

    @Test
    void agencyManagesItsOwnPoolOnly() throws Exception {
        Cookie nadia = login("nadia");
        JsonNode created = call(post("/supplier/workers").cookie(nadia).contentType(MediaType.APPLICATION_JSON)
                .content(workerJson("Adam", "Pool")), status().isCreated());
        long id = created.get("id").asLong();
        assertThat(created.get("city").asText()).isEqualTo("Roubaix");

        JsonNode pool = call(get("/supplier/workers").cookie(nadia), status().isOk());
        assertThat(pool.get("workers").findValuesAsText("lastName")).contains("Pool", "Haddad");
        assertThat(pool.get("cities")).isNotEmpty();
        assertThat(call(get("/supplier/workers").cookie(login("thomas")), status().isOk()).get("workers").findValuesAsText("lastName"))
                .doesNotContain("Pool", "Haddad");

        call(put("/supplier/workers/" + id).cookie(login("thomas")).contentType(MediaType.APPLICATION_JSON)
                .content(workerJson("Adam", "Hijack")), status().isNotFound());
        JsonNode bad = call(post("/supplier/workers").cookie(nadia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"X\",\"city\":\"Atlantis\"}"), status().isBadRequest());
        assertThat(bad.get("detail").asText()).contains("city must be one of");
        call(get("/supplier/workers").cookie(login("claire")), status().isForbidden());
    }

    @Test
    void agencyProposesAndWithdrawsItsOwnWorkersOnAPublishedOrder() throws Exception {
        Cookie nadia = login("nadia");
        Cookie claire = login("claire");
        long order = new MissionFlow(this::call, json, claire).idOf(MissionService.ref(147));
        long workerId = call(post("/supplier/workers").cookie(nadia).contentType(MediaType.APPLICATION_JSON)
                .content(workerJson("Lina", "Propose")), status().isCreated()).get("id").asLong();

        JsonNode ws = call(post("/supplier/orders/" + order + "/proposals").cookie(nadia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"workerId\":" + workerId + "}"), status().isOk());
        JsonNode proposal = byWorker(ws.get("proposals"), workerId);
        assertThat(proposal.get("eligible").asBoolean()).isTrue();
        assertThat(proposal.get("rank").asInt()).isPositive();
        call(post("/supplier/orders/" + order + "/proposals").cookie(nadia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"workerId\":" + workerId + "}"), status().isConflict());

        JsonNode buyerView = call(get("/missions/" + order + "/sourcing").cookie(claire), status().isOk());
        assertThat(buyerView.get("candidates").findValuesAsText("workerName")).contains("Lina Propose");

        // Another agency can neither propose this worker nor touch the proposal.
        call(post("/supplier/orders/" + order + "/proposals").cookie(login("thomas")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"workerId\":" + workerId + "}"), status().isNotFound());
        long candidateId = proposal.get("candidateId").asLong();
        call(delete("/supplier/orders/" + order + "/proposals/" + candidateId).cookie(login("thomas")), status().isNotFound());

        JsonNode after = call(delete("/supplier/orders/" + order + "/proposals/" + candidateId).cookie(nadia), status().isOk());
        assertThat(after.get("proposals").findValuesAsText("workerName")).doesNotContain("Lina Propose");

        long metalPro = new MissionFlow(this::call, json, login("marc")).idOf(MissionService.ref(145));
        call(get("/supplier/orders/" + metalPro + "/workspace").cookie(nadia), status().isNotFound());
    }

    @Test
    void agencySignsContractsSubmitsTimesheetsAndIssuesCreditNotes() throws Exception {
        Cookie claire = login("claire");
        Cookie nadia = login("nadia");
        MissionFlow flow = new MissionFlow(this::call, json, claire);
        long id = flow.create("Objet : Préparateur Roubaix\n\nBonjour,\nNous cherchons 1 préparateur de commandes pour la "
                + "plateforme Roubaix, du lundi 14 décembre 2026 au vendredi 18 décembre 2026.\nHoraires : du lundi au vendredi "
                + "13h00-20h00 (35 h hebdo), pas d'heures supplémentaires.\nTaux horaire : 12,10 €.\n"
                + "Motif : accroissement temporaire d'activité.");
        JsonNode intake = call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        for (JsonNode f : intake.get("fields")) {
            if (f.get("needsReview").asBoolean()) {
                call(post("/missions/" + id + "/intake/fields/" + f.get("name").asText() + "/confirm").cookie(claire), status().isOk());
            }
        }
        flow.finalize(id, "intake");
        JsonNode sourcing = call(post("/missions/" + id + "/sourcing/publish").cookie(claire), status().isOk());
        long candidate = -1;
        for (JsonNode c : sourcing.get("candidates")) {
            if (c.get("supplierName").asText().equals(INTERSUD)) {
                candidate = c.get("candidateId").asLong();
                break;
            }
        }
        assertThat(candidate).isPositive();
        call(post("/missions/" + id + "/sourcing/select/" + candidate).cookie(claire), status().isOk());
        flow.finalize(id, "sourcing");

        // Contracts: the agency cannot sign a contract with a blocking issue; after the client's fix it signs its side.
        JsonNode contracts = call(post("/missions/" + id + "/contracts/generate").cookie(claire), status().isOk());
        long contractId = contracts.get("contracts").get(0).get("id").asLong();
        call(post("/supplier/contracts/" + contractId + "/sign").cookie(nadia), status().isConflict());
        call(post("/contracts/" + contractId + "/fix/DATES_WITHIN_ORDER").cookie(claire), status().isOk());
        call(post("/supplier/contracts/" + contractId + "/sign").cookie(login("thomas")), status().isNotFound());
        JsonNode signedBySupplier = call(post("/supplier/contracts/" + contractId + "/sign").cookie(nadia), status().isOk());
        assertThat(signedBySupplier.get("status").asText()).isEqualTo("DRAFT");
        String supplierSignedAt = signedBySupplier.get("signedBySupplierAt").asText();
        JsonNode signed = call(post("/missions/" + id + "/contracts/sign").cookie(claire), status().isOk());
        assertThat(signed.get("contracts").get(0).get("status").asText()).isEqualTo("SIGNED");
        assertThat(java.time.Instant.parse(signed.get("contracts").get(0).get("signedBySupplierAt").asText()).toEpochMilli())
                .isEqualTo(java.time.Instant.parse(supplierSignedAt).toEpochMilli());
        flow.finalize(id, "contracts");

        // Timesheets: a correction by the agency discards the earlier check, so the client must check again.
        call(post("/missions/" + id + "/timesheets/check").cookie(claire), status().isOk());
        JsonNode ws = call(get("/supplier/orders/" + id + "/workspace").cookie(nadia), status().isOk());
        assertThat(ws.get("anomalies")).hasSize(1);
        long sheet = ws.get("timesheets").get(0).get("id").asLong();
        call(put("/supplier/timesheets/" + sheet).cookie(nadia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyHours\":[7,7,7,7,30,0,0]}"), status().isBadRequest());
        call(put("/supplier/timesheets/" + sheet).cookie(login("thomas")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyHours\":[7,7,7,7,7,0,0]}"), status().isNotFound());
        JsonNode corrected = call(put("/supplier/timesheets/" + sheet).cookie(nadia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyHours\":[7,7,7,7,7,0,0]}"), status().isOk());
        assertThat(corrected.get("total").decimalValue()).isEqualByComparingTo("35");
        JsonNode gate = call(get("/missions/" + id).cookie(claire), status().isOk()).get("gate").get("checks").get(0);
        assertThat(gate.get("passed").asBoolean()).isFalse();
        JsonNode rechecked = call(post("/missions/" + id + "/timesheets/check").cookie(claire), status().isOk());
        assertThat(rechecked.get("anomalies").findValuesAsText("status")).doesNotContain("OPEN");
        call(post("/missions/" + id + "/timesheets/approve").cookie(claire), status().isOk());
        call(put("/supplier/timesheets/" + sheet).cookie(nadia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyHours\":[8,7,7,7,7,0,0]}"), status().isConflict());
        flow.finalize(id, "timesheets");

        // Invoice: the agency issues the credit note itself; the match then passes.
        JsonNode received = call(post("/missions/" + id + "/invoice/receive").cookie(claire), status().isOk());
        long invoiceId = received.get("invoices").get(0).get("id").asLong();
        assertThat(received.get("invoices").get(0).get("status").asText()).isEqualTo("MISMATCH");
        JsonNode credited = call(post("/supplier/invoices/" + invoiceId + "/credit-note").cookie(nadia), status().isOk());
        assertThat(credited.get("status").asText()).isEqualTo("MATCHED");
        call(post("/supplier/invoices/" + invoiceId + "/credit-note").cookie(nadia), status().isConflict());
        call(post("/missions/" + id + "/invoice/approve").cookie(claire), status().isOk());

        JsonNode audit = call(get("/missions/" + id + "/audit").cookie(claire), status().isOk());
        assertThat(audit.findValuesAsText("action")).contains("contract.signed.supplier", "timesheet.submitted.supplier",
                "invoice.credit-note.received");
        JsonNode dashboard = call(get("/supplier/dashboard").cookie(nadia), status().isOk());
        assertThat(dashboard.get("workers").asInt()).isPositive();
    }

    static JsonNode byWorker(JsonNode proposals, long workerId) {
        for (JsonNode p : proposals) {
            if (p.get("workerId").asLong() == workerId) {
                return p;
            }
        }
        throw new AssertionError("worker " + workerId + " not proposed: " + proposals);
    }
}
