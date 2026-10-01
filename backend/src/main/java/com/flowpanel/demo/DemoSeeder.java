package com.flowpanel.demo;

import com.flowpanel.auth.CurrentUser;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.auth.Role;
import com.flowpanel.contract.ContractService;
import com.flowpanel.copilot.DocumentService;
import com.flowpanel.intake.IntakeService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionRepository;
import com.flowpanel.mission.MissionService;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.RequestTemplate;
import com.flowpanel.mission.RequestTemplateRepository;
import com.flowpanel.sourcing.SourcingService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds the demo missions by driving them through the real services and gates, so seeded data is always
 * consistent with what a user would produce.
 */
@Component
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    static final CurrentUser CLAIRE = new CurrentUser(DemoData.CLAIRE, "Claire Dubois", Role.BUYER, DemoData.LOGINORD, null);
    static final long KARIM = 1;
    static final long LUCAS = 6;
    static final CurrentUser MARC = new CurrentUser(DemoData.MARC, "Marc Lefèvre", Role.BUYER, DemoData.METALPRO, null);

    private final MissionRepository missions;
    private final MissionService missionService;
    private final RequestTemplateRepository templates;
    private final IntakeService intake;
    private final SourcingService sourcing;
    private final ContractService contracts;
    private final DocumentService documents;
    private final RequestContext context;
    private final JdbcTemplate jdbc;
    private final boolean seedOnStartup;
    private final TransactionTemplate tx;

    public DemoSeeder(MissionRepository missions, MissionService missionService, RequestTemplateRepository templates,
                      IntakeService intake, SourcingService sourcing,
                      ContractService contracts, DocumentService documents, RequestContext context, JdbcTemplate jdbc, TransactionTemplate tx,
                      @Value("${flowpanel.demo.seed-on-startup:true}") boolean seedOnStartup) {
        this.missions = missions;
        this.missionService = missionService;
        this.templates = templates;
        this.intake = intake;
        this.sourcing = sourcing;
        this.contracts = contracts;
        this.documents = documents;
        this.context = context;
        this.jdbc = jdbc;
        this.seedOnStartup = seedOnStartup;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (seedOnStartup) {
            tx.executeWithoutResult(status -> seedDocuments());
        }
        if (seedOnStartup && missions.count() == 0) {
            tx.executeWithoutResult(status -> seed());
            log.info("Demo data seeded");
        }
    }

    /** Ingests the synthetic policy documents of each tenant once (chunked and embedded, cached by content hash). */
    void seedDocuments() {
        seedDocuments(CLAIRE, "loginord");
        seedDocuments(MARC, "metalpro");
    }

    private void seedDocuments(CurrentUser user, String tenantCode) {
        if (documents.count(user.tenantId()) > 0) {
            return;
        }
        try {
            var resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources("classpath:documents/" + tenantCode + "/*.md");
            java.util.Arrays.sort(resources, java.util.Comparator.comparing(r -> String.valueOf(r.getFilename())));
            for (var r : resources) {
                String content = new String(r.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String title = content.lines().filter(l -> l.startsWith("# ")).findFirst().map(l -> l.substring(2).strip())
                        .orElse(r.getFilename());
                context.runAs(user, () -> documents.ingest(user.tenantId(), title, "SEED", "text/markdown", content));
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Wipes every mission (cascading to phase data) and seeds the demo again. */
    @Transactional
    public int reset() {
        jdbc.execute("TRUNCATE mission CASCADE");
        jdbc.update("DELETE FROM audit_event WHERE mission_id IS NOT NULL");
        jdbc.execute("ALTER SEQUENCE mission_number_seq RESTART WITH 150");
        seed();
        return (int) missions.count();
    }

    @Transactional
    public void seed() {
        context.runAs(CLAIRE, () -> {
            seedTimesheetsMission();
            seedSourcingMission();
        });
        context.runAs(MARC, this::seedMetalProMission);
    }

    /** ORD-2026-0142: two forklift operators in Lille Lesquin, advanced to TIMESHEETS. */
    private void seedTimesheetsMission() {
        Mission m = missionService.createSeeded(DemoData.LOGINORD, 142, "Caristes CACES 3 — inventaire Lesquin",
                "Entrepôt Lille Lesquin", null, DemoData.EMAIL_0142, DemoData.CLAIRE);
        advance(m, Phase.TIMESHEETS, List.of(KARIM, LUCAS));
    }

    /** ORD-2026-0147: three order pickers in Roubaix, advanced to SOURCING. */
    private void seedSourcingMission() {
        RequestTemplate t = templates.findById("pickers-roubaix").orElseThrow();
        Mission m = missionService.createSeeded(DemoData.LOGINORD, 147, t.getTitle(), t.getSite(), t.getCode(),
                t.getEmailText(), DemoData.CLAIRE);
        advance(m, Phase.SOURCING, List.of());
        sourcing.publish(m.getId());
    }

    /** ORD-2026-0145: a MétalPro mission, kept at INTAKE (shows tenant isolation). */
    private void seedMetalProMission() {
        missionService.createSeeded(DemoData.METALPRO, 145, "Opérateurs de production — Valenciennes",
                "Usine de Valenciennes", null, DemoData.EMAIL_0145, DemoData.MARC);
    }

    private void advance(Mission m, Phase target, List<Long> preferredWorkers) {
        while (m.getPhase() != target) {
            completePhase(m, preferredWorkers);
            missionService.finalizePhase(m.getId(), m.getPhase());
        }
    }

    /** Real AI extraction (mock profile), then the flagged fields are confirmed as the AI proposed them. */
    private void completeIntake(Mission m) {
        intake.extract(m.getId()).fields().stream()
                .filter(IntakeService.FieldView::needsReview)
                .forEach(f -> intake.confirm(m.getId(), f.name(), new IntakeService.ConfirmRequest(null)));
    }

    /** Publishes, then selects the preferred workers (or the top-ranked ones) until all positions are filled. */
    private void completeSourcing(Mission m, List<Long> preferredWorkers) {
        var view = sourcing.publish(m.getId());
        var ranked = new java.util.ArrayList<>(view.candidates());
        ranked.sort(java.util.Comparator.comparing((SourcingService.CandidateView c) -> !preferredWorkers.contains(c.workerId())));
        for (var c : ranked) {
            if (sourcing.view(m.getId()).filled() >= view.quantity()) {
                break;
            }
            sourcing.select(m.getId(), c.candidateId());
        }
    }

    /** Generates the contracts, applies the auto-fixes for the seeded blocking issue, then signs. */
    private void completeContracts(Mission m) {
        for (var c : contracts.generate(m.getId()).contracts()) {
            c.checks().stream().filter(r -> !r.passed() && r.autoFixable())
                    .forEach(r -> contracts.fix(c.id(), r.ruleId()));
        }
        contracts.sign(m.getId());
    }

    private void completePhase(Mission m, List<Long> preferredWorkers) {
        switch (m.getPhase()) {
            case INTAKE -> completeIntake(m);
            case SOURCING -> completeSourcing(m, preferredWorkers);
            case CONTRACTS -> completeContracts(m);
            default -> throw new IllegalStateException("Seeder cannot complete phase " + m.getPhase());
        }
    }
}
