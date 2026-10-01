package com.flowpanel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

/** Drives a fresh mission through the real endpoints up to a given phase (test helper). */
public class MissionFlow {

    @FunctionalInterface
    public interface Caller {
        JsonNode call(RequestBuilder request, ResultMatcher expected) throws Exception;
    }

    private final Caller api;
    private final ObjectMapper json;
    private final Cookie buyer;

    public MissionFlow(Caller api, ObjectMapper json, Cookie buyer) {
        this.api = api;
        this.json = json;
        this.buyer = buyer;
    }

    /** Two forklift operators in Lesquin for the given French dates, e.g. "lundi 2 novembre 2026". */
    public static String forkliftEmail(String from, String to) {
        return "Objet : Caristes Lesquin\n\nBonjour,\nNous avons besoin de 2 caristes CACES R489 cat. 3 sur l'entrepôt Lille Lesquin, "
                + "du " + from + " au " + to + ".\nHoraires : du lundi au vendredi, 6h00-13h00, soit 35 h par semaine. "
                + "Pas d'heures supplémentaires.\nTaux horaire : 13,20 € brut.\nMotif : accroissement temporaire d'activité.";
    }

    public long create(String email) throws Exception {
        return api.call(post("/missions").cookie(buyer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("emailText", email))), status().isCreated()).get("id").asLong();
    }

    public long missionAtSourcing(String from, String to) throws Exception {
        long id = create(forkliftEmail(from, to));
        JsonNode intake = api.call(post("/missions/" + id + "/intake/extract").cookie(buyer), status().isOk());
        for (JsonNode f : intake.get("fields")) {
            if (f.get("needsReview").asBoolean()) {
                api.call(post("/missions/" + id + "/intake/fields/" + f.get("name").asText() + "/confirm").cookie(buyer), status().isOk());
            }
        }
        finalize(id, "intake");
        return id;
    }

    public long missionAtContracts(String from, String to) throws Exception {
        long id = missionAtSourcing(from, to);
        JsonNode sourcing = api.call(post("/missions/" + id + "/sourcing/publish").cookie(buyer), status().isOk());
        int quantity = sourcing.get("quantity").asInt();
        for (int i = 0; i < quantity; i++) {
            api.call(post("/missions/" + id + "/sourcing/select/" + sourcing.get("candidates").get(i).get("candidateId").asLong())
                    .cookie(buyer), status().isOk());
        }
        finalize(id, "sourcing");
        return id;
    }

    public long missionAtTimesheets(String from, String to) throws Exception {
        long id = missionAtContracts(from, to);
        JsonNode contracts = api.call(post("/missions/" + id + "/contracts/generate").cookie(buyer), status().isOk());
        for (JsonNode c : contracts.get("contracts")) {
            for (JsonNode r : c.get("checks")) {
                if (!r.get("passed").asBoolean() && r.get("autoFixable").asBoolean()) {
                    api.call(post("/contracts/" + c.get("id").asLong() + "/fix/" + r.get("ruleId").asText()).cookie(buyer), status().isOk());
                }
            }
        }
        api.call(post("/missions/" + id + "/contracts/sign").cookie(buyer), status().isOk());
        finalize(id, "contracts");
        return id;
    }

    /** Timesheets checked, the seeded anomaly resolved with the given resolution, approved, phase finalized. */
    public long missionAtInvoice(String from, String to, String resolution) throws Exception {
        long id = missionAtTimesheets(from, to);
        JsonNode checked = api.call(post("/missions/" + id + "/timesheets/check").cookie(buyer), status().isOk());
        for (JsonNode a : checked.get("anomalies")) {
            if ("OPEN".equals(a.get("status").asText())) {
                api.call(post("/anomalies/" + a.get("id").asLong() + "/resolve").cookie(buyer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"" + resolution + "\"}"), status().isOk());
            }
        }
        api.call(post("/missions/" + id + "/timesheets/approve").cookie(buyer), status().isOk());
        finalize(id, "timesheets");
        return id;
    }

    public JsonNode finalize(long id, String phase) throws Exception {
        return api.call(post("/missions/" + id + "/phases/" + phase + "/finalize").cookie(buyer), status().isOk());
    }

    public long idOf(String ref) throws Exception {
        for (JsonNode m : api.call(get("/missions").cookie(buyer), status().isOk())) {
            if (m.get("ref").asText().equals(ref)) {
                return m.get("id").asLong();
            }
        }
        throw new AssertionError(ref + " not on the board");
    }
}
