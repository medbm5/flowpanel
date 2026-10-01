package com.flowpanel.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.MissionFlow;
import com.flowpanel.ai.AiModelClient;
import com.flowpanel.ai.mock.MockAiModelClient;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class InvoiceIT extends AbstractIntegrationTest {

    @Autowired
    AiModelClient client;

    Cookie claire;
    MissionFlow flow;

    @BeforeEach
    void setUp() throws Exception {
        claire = login("claire");
        flow = new MissionFlow(this::call, json, claire);
    }

    @Test
    void receiveMatchCreditNoteApproveAndClose() throws Exception {
        long id = flow.missionAtInvoice("lundi 7 décembre 2026", "vendredi 18 décembre 2026", "APPROVE_OVERTIME");
        call(post("/missions/" + id + "/invoice/approve").cookie(claire), status().isConflict());

        JsonNode received = call(post("/missions/" + id + "/invoice/receive").cookie(claire), status().isOk());
        assertThat(received.get("invoices")).isNotEmpty();
        JsonNode mismatch = null;
        for (JsonNode inv : received.get("invoices")) {
            assertThat(inv.get("aiExtracted").asBoolean()).isTrue();
            if ("MISMATCH".equals(inv.get("status").asText())) {
                mismatch = inv;
            }
        }
        assertThat(mismatch).isNotNull();
        JsonNode badLine = mismatch.get("match").get("lines").get(0);
        assertThat(badLine.get("status").asText()).isEqualTo("HOURS_MISMATCH");
        assertThat(new BigDecimal(badLine.get("delta").asText())).isEqualByComparingTo("39.60");
        String message = mismatch.get("aiMessageDraft").asText();
        assertThat(message).contains("avoir").contains("39.60").contains("Claire Dubois");
        assertThat(((MockAiModelClient) client).recentCalls().stream()
                .filter(c -> c.feature().equals(InvoiceService.MESSAGE_FEATURE)).toList().getLast().user())
                .doesNotContain("Claire Dubois").doesNotContain(badLine.get("workerName").asText());

        JsonNode mission = call(get("/missions/" + id).cookie(claire), status().isOk());
        assertThat(mission.get("needsReview").asBoolean()).isTrue();
        call(post("/missions/" + id + "/invoice/approve").cookie(claire), status().isConflict());

        JsonNode credited = call(post("/missions/" + id + "/invoice/request-credit-note").cookie(claire), status().isOk());
        assertThat(new BigDecimal(credited.get("creditNotes").asText())).isEqualByComparingTo("39.60");
        credited.get("invoices").forEach(i -> assertThat(i.get("status").asText()).isEqualTo("MATCHED"));
        call(post("/missions/" + id + "/invoice/request-credit-note").cookie(claire), status().isConflict());

        call(post("/missions/" + id + "/invoice/approve").cookie(claire), status().isOk());
        JsonNode closed = flow.finalize(id, "invoice");
        assertThat(closed.get("phase").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("closed").asBoolean()).isTrue();
        JsonNode summary = closed.get("summary");
        assertThat(summary.get("workersPlaced").asInt()).isEqualTo(2);
        assertThat(new BigDecimal(summary.get("hoursApproved").asText())).isEqualByComparingTo("146");
        assertThat(new BigDecimal(summary.get("amountApproved").asText())).isEqualByComparingTo("1927.20");
        assertThat(new BigDecimal(summary.get("overbillingAvoided").asText())).isEqualByComparingTo("39.60");
        assertThat(summary.get("aiStepsReviewed").asInt()).isPositive();
        assertThat(closed.get("phases").get(5).get("state").asText()).isEqualTo("DONE");

        call(get("/missions/" + id + "/summary").cookie(claire), status().isOk());
        JsonNode closedBoard = call(get("/missions?status=closed").cookie(claire), status().isOk());
        assertThat(closedBoard.findValuesAsText("id")).contains(String.valueOf(id));
        call(post("/missions/" + id + "/phases/closed/finalize").cookie(claire), status().isConflict());
    }

    @Test
    void pdfIsServedToTheTenantOnlyAndSupplierSeesItsInvoices() throws Exception {
        long id = flow.missionAtInvoice("lundi 21 décembre 2026", "jeudi 31 décembre 2026", "RETURN_TO_SUPPLIER");
        JsonNode received = call(post("/missions/" + id + "/invoice/receive").cookie(claire), status().isOk());
        long invoiceId = received.get("invoices").get(0).get("id").asLong();
        mvc.perform(get("/invoices/" + invoiceId + "/pdf").cookie(claire))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        call(get("/invoices/" + invoiceId + "/pdf").cookie(login("marc")), status().isNotFound());

        String supplierName = received.get("invoices").get(0).get("supplierName").asText();
        String persona = supplierName.startsWith("InterSud") ? "nadia" : supplierName.startsWith("Proxi") ? "thomas" : null;
        if (persona != null) {
            JsonNode mine = call(get("/supplier/invoices").cookie(login(persona)), status().isOk());
            assertThat(mine.findValuesAsText("ref")).contains(received.get("invoices").get(0).get("ref").asText());
            mine.forEach(i -> assertThat(i.get("ref").asText()).contains(persona.equals("nadia") ? "INTERSUD" : "PROXI"));
        }
    }

    @Test
    void textExtractionEndpointForEvals() throws Exception {
        String text = "FACTURE N° INV-TEST-1\nFournisseur : Atlas RH\nIntérimaire | Heures | Taux horaire | Montant HT\n"
                + "Yanis Leroy | 35,00 h | 13,20 €/h | 462,00 €\nTotal HT : 462,00 €";
        JsonNode lines = call(post("/invoices/extract").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("text", text))), status().isOk());
        assertThat(lines.get("lines").get(0).get("workerName").asText()).isEqualTo("Yanis Leroy");
        assertThat(lines.get("totalExclTax").decimalValue()).isEqualByComparingTo("462.00");
    }
}
