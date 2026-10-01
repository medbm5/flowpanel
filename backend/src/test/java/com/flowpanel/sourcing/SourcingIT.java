package com.flowpanel.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.mission.MissionService;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SourcingIT extends AbstractIntegrationTest {

    Cookie claire;

    @BeforeEach
    void setUp() throws Exception {
        claire = login("claire");
    }

    @Test
    void rankingAppliesHardRulesWithReadableReasons() throws Exception {
        long id = missionAtSourcing(Map.of("templateCode", "forklift-lille"));
        JsonNode view = call(post("/missions/" + id + "/sourcing/publish").cookie(claire), status().isOk());

        assertThat(view.get("published").asBoolean()).isTrue();
        assertThat(view.get("suppliers").toString()).contains("InterSud Intérim", "Proxi Staffing", "Atlas RH");
        assertThat(view.get("quantity").asInt()).isEqualTo(2);
        assertThat(reasonsOf(view, "Karim Haddad")).anyMatch(r -> r.startsWith("Already placed on " + MissionService.ref(142)));
        assertThat(reasonsOf(view, "Lucas Petit")).anyMatch(r -> r.startsWith("Already placed on " + MissionService.ref(142)));
        assertThat(reasonsOf(view, "Emma Lambert")).anyMatch(r -> r.startsWith("Unavailable for the full period"));
        assertThat(reasonsOf(view, "Camille Dupuis")).contains("Missing certification: CACES R489 cat. 3");

        for (JsonNode c : view.get("candidates")) {
            assertThat(c.get("eligible").asBoolean()).isTrue();
            assertThat(c.get("explanation")).isNotEmpty();
        }
        JsonNode mission = call(get("/missions/" + id).cookie(claire), status().isOk());
        assertThat(mission.get("gate").get("checks").get(0).get("passed").asBoolean()).isTrue();
        assertThat(mission.get("gate").get("checks").get(1).get("label").asText()).isEqualTo("Positions filled (0/2)");

        call(post("/missions/" + id + "/sourcing/publish").cookie(claire), status().isConflict());
    }

    @Test
    void topCandidatesGetAnAiSummaryAndRanksAreSequential() throws Exception {
        long id = missionAtSourcing(Map.of("emailText", forklift("lundi 2 novembre 2026", "vendredi 6 novembre 2026")));
        JsonNode view = call(post("/missions/" + id + "/sourcing/publish").cookie(claire), status().isOk());
        JsonNode first = view.get("candidates").get(0);
        assertThat(first.get("rank").asInt()).isEqualTo(1);
        assertThat(first.get("aiSummary").asText()).isNotBlank();
        assertThat(view.get("candidates").get(1).get("score").asDouble()).isLessThanOrEqualTo(first.get("score").asDouble());
    }

    @Test
    void selectionIsCappedAtTheOrderQuantityAndCanBeUndone() throws Exception {
        long id = missionAtSourcing(Map.of("emailText", pickers("lundi 9 novembre 2026", "vendredi 13 novembre 2026", 2)));
        JsonNode view = call(post("/missions/" + id + "/sourcing/publish").cookie(claire), status().isOk());
        List<Long> ids = candidateIds(view);
        call(post("/missions/" + id + "/sourcing/select/" + ids.get(0)).cookie(claire), status().isOk());
        JsonNode two = call(post("/missions/" + id + "/sourcing/select/" + ids.get(1)).cookie(claire), status().isOk());
        assertThat(two.get("filled").asInt()).isEqualTo(2);
        JsonNode full = call(post("/missions/" + id + "/sourcing/select/" + ids.get(2)).cookie(claire), status().isConflict());
        assertThat(full.get("detail").asText()).contains("positions are already filled");

        JsonNode after = call(delete("/missions/" + id + "/sourcing/select/" + ids.get(1)).cookie(claire), status().isOk());
        assertThat(after.get("filled").asInt()).isEqualTo(1);

        call(post("/missions/" + id + "/sourcing/select/" + ids.get(2)).cookie(claire), status().isOk());
        JsonNode finalized = call(post("/missions/" + id + "/phases/sourcing/finalize").cookie(claire), status().isOk());
        assertThat(finalized.get("phase").asText()).isEqualTo("CONTRACTS");
        assertThat(finalized.get("positions").get("filled").asInt()).isEqualTo(2);
    }

    @Test
    void excludedCandidatesCannotBeSelected() throws Exception {
        long id = missionAtSourcing(Map.of("templateCode", "forklift-lille"));
        JsonNode view = call(post("/missions/" + id + "/sourcing/publish").cookie(claire), status().isOk());
        long excluded = view.get("excluded").get(0).get("candidateId").asLong();
        call(post("/missions/" + id + "/sourcing/select/" + excluded).cookie(claire), status().isConflict());
    }

    @Test
    void doubleBookingAcrossMissionsIsRejectedWithTheConflictingRef() throws Exception {
        String email = forklift("lundi 16 novembre 2026", "vendredi 20 novembre 2026");
        long a = missionAtSourcing(Map.of("emailText", email));
        long b = missionAtSourcing(Map.of("emailText", email));
        JsonNode viewA = call(post("/missions/" + a + "/sourcing/publish").cookie(claire), status().isOk());
        JsonNode viewB = call(post("/missions/" + b + "/sourcing/publish").cookie(claire), status().isOk());
        long workerId = viewA.get("candidates").get(0).get("workerId").asLong();

        call(post("/missions/" + a + "/sourcing/select/" + candidateFor(viewA, workerId)).cookie(claire), status().isOk());
        JsonNode problem = call(post("/missions/" + b + "/sourcing/select/" + candidateFor(viewB, workerId)).cookie(claire),
                status().isConflict());
        String refA = call(get("/missions/" + a).cookie(claire), status().isOk()).get("ref").asText();
        assertThat(problem.get("detail").asText()).contains("already placed on " + refA);
        assertThat(problem.get("conflictingMission").asText()).isEqualTo(refA);
    }

    @Test
    void concurrentSelectionsOfTheSameWorkerOnOverlappingMissionsYieldExactlyOneSuccess() throws Exception {
        String email = forklift("lundi 23 novembre 2026", "vendredi 27 novembre 2026");
        long a = missionAtSourcing(Map.of("emailText", email));
        long b = missionAtSourcing(Map.of("emailText", email));
        JsonNode viewA = call(post("/missions/" + a + "/sourcing/publish").cookie(claire), status().isOk());
        JsonNode viewB = call(post("/missions/" + b + "/sourcing/publish").cookie(claire), status().isOk());
        long workerId = viewA.get("candidates").get(0).get("workerId").asLong();
        long ca = candidateFor(viewA, workerId);
        long cb = candidateFor(viewB, workerId);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> selectStatus(start, a, ca),
                    () -> selectStatus(start, b, cb));
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> t : tasks) {
                futures.add(pool.submit(t));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void supplierUsersSeeOnlyTheirOwnProposals() throws Exception {
        long seeded = idOfRef(MissionService.ref(147));
        Cookie nadia = login("nadia");
        Cookie thomas = login("thomas");

        JsonNode orders = call(get("/supplier/orders").cookie(nadia), status().isOk());
        assertThat(orders.findValuesAsText("ref")).contains(MissionService.ref(147));

        JsonNode interSud = call(get("/supplier/orders/" + seeded).cookie(nadia), status().isOk());
        assertThat(interSud.get("myProposals")).isNotEmpty();
        interSud.get("myProposals").forEach(p -> assertThat(p.get("supplierName").asText()).isEqualTo("InterSud Intérim"));

        JsonNode proxi = call(get("/supplier/orders/" + seeded).cookie(thomas), status().isOk());
        proxi.get("myProposals").forEach(p -> assertThat(p.get("supplierName").asText()).isEqualTo("Proxi Staffing"));

        long metalPro = idOfRefFor(login("marc"), MissionService.ref(145));
        call(get("/supplier/orders/" + metalPro).cookie(nadia), status().isNotFound());
        call(get("/supplier/orders").cookie(claire), status().isForbidden());
        call(get("/missions/" + seeded).cookie(nadia), status().isForbidden());
    }

    @Test
    void sourcingWritesAreRejectedOutsideTheSourcingPhase() throws Exception {
        long intakeMission = call(post("/missions").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateCode\":\"office-paris\"}"), status().isCreated()).get("id").asLong();
        call(post("/missions/" + intakeMission + "/sourcing/publish").cookie(claire), status().isConflict());
        call(get("/missions/" + intakeMission + "/sourcing").cookie(claire), status().isConflict());
    }

    // ------------------------------------------------------------------ helpers

    private int selectStatus(CountDownLatch start, long mission, long candidate) throws Exception {
        start.await();
        return mvc.perform(post("/missions/" + mission + "/sourcing/select/" + candidate).cookie(claire))
                .andReturn().getResponse().getStatus();
    }

    static String forklift(String from, String to) {
        return "Objet : Caristes Lesquin\n\nBonjour,\nNous avons besoin de 2 caristes CACES R489 cat. 3 sur l'entrepôt Lille Lesquin, "
                + "du " + from + " au " + to + ".\nHoraires : du lundi au vendredi, 6h00-13h00, soit 35 h par semaine. "
                + "Pas d'heures supplémentaires.\nTaux horaire : 13,20 € brut.\nMotif : accroissement temporaire d'activité.";
    }

    static String pickers(String from, String to, int quantity) {
        return "Objet : Préparateurs Roubaix\n\nBonjour,\nNous cherchons " + quantity + " préparateurs de commandes pour la "
                + "plateforme Roubaix, du " + from + " au " + to + ".\nHoraires : du lundi au vendredi 13h00-20h00 (35 h hebdo), "
                + "pas d'heures supplémentaires.\nTaux horaire : 12,10 €.\nMotif : accroissement temporaire d'activité.";
    }

    long missionAtSourcing(Map<String, String> body) throws Exception {
        long id = call(post("/missions").cookie(claire).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), status().isCreated()).get("id").asLong();
        JsonNode view = call(post("/missions/" + id + "/intake/extract").cookie(claire), status().isOk());
        for (JsonNode f : view.get("fields")) {
            if (f.get("needsReview").asBoolean()) {
                call(post("/missions/" + id + "/intake/fields/" + f.get("name").asText() + "/confirm").cookie(claire), status().isOk());
            }
        }
        call(post("/missions/" + id + "/phases/intake/finalize").cookie(claire), status().isOk());
        return id;
    }

    static List<String> reasonsOf(JsonNode view, String worker) {
        for (JsonNode c : view.get("excluded")) {
            if (c.get("workerName").asText().equals(worker)) {
                List<String> reasons = new ArrayList<>();
                c.get("exclusionReasons").forEach(r -> reasons.add(r.asText()));
                return reasons;
            }
        }
        throw new AssertionError(worker + " is not excluded");
    }

    static List<Long> candidateIds(JsonNode view) {
        List<Long> ids = new ArrayList<>();
        view.get("candidates").forEach(c -> ids.add(c.get("candidateId").asLong()));
        return ids;
    }

    static long candidateFor(JsonNode view, long workerId) {
        for (JsonNode c : view.get("candidates")) {
            if (c.get("workerId").asLong() == workerId) {
                return c.get("candidateId").asLong();
            }
        }
        throw new AssertionError("worker " + workerId + " not eligible");
    }

    private long idOfRef(String ref) throws Exception {
        return idOfRefFor(claire, ref);
    }

    private long idOfRefFor(Cookie who, String ref) throws Exception {
        for (JsonNode m : call(get("/missions").cookie(who), status().isOk())) {
            if (m.get("ref").asText().equals(ref)) {
                return m.get("id").asLong();
            }
        }
        throw new AssertionError(ref);
    }
}
