package com.flowpanel.demo;

import com.flowpanel.auth.CurrentUser;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.auth.Role;
import com.flowpanel.intake.IntakeService;
import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionRepository;
import com.flowpanel.mission.MissionService;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.RequestTemplate;
import com.flowpanel.mission.RequestTemplateRepository;
import java.util.Map;
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
    static final CurrentUser MARC = new CurrentUser(DemoData.MARC, "Marc Lefèvre", Role.BUYER, DemoData.METALPRO, null);

    private final MissionRepository missions;
    private final MissionService missionService;
    private final RequestTemplateRepository templates;
    private final ArtifactService artifacts;
    private final IntakeService intake;
    private final RequestContext context;
    private final JdbcTemplate jdbc;
    private final boolean seedOnStartup;
    private final TransactionTemplate tx;

    public DemoSeeder(MissionRepository missions, MissionService missionService, RequestTemplateRepository templates,
                      ArtifactService artifacts, IntakeService intake, RequestContext context, JdbcTemplate jdbc, TransactionTemplate tx,
                      @Value("${flowpanel.demo.seed-on-startup:true}") boolean seedOnStartup) {
        this.missions = missions;
        this.missionService = missionService;
        this.templates = templates;
        this.artifacts = artifacts;
        this.intake = intake;
        this.context = context;
        this.jdbc = jdbc;
        this.seedOnStartup = seedOnStartup;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (seedOnStartup && missions.count() == 0) {
            tx.executeWithoutResult(status -> seed());
            log.info("Demo data seeded");
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
        advance(m, Phase.TIMESHEETS);
    }

    /** ORD-2026-0147: three order pickers in Roubaix, advanced to SOURCING. */
    private void seedSourcingMission() {
        RequestTemplate t = templates.findById("pickers-roubaix").orElseThrow();
        Mission m = missionService.createSeeded(DemoData.LOGINORD, 147, t.getTitle(), t.getSite(), t.getCode(),
                t.getEmailText(), DemoData.CLAIRE);
        advance(m, Phase.SOURCING);
    }

    /** ORD-2026-0145: a MétalPro mission, kept at INTAKE (shows tenant isolation). */
    private void seedMetalProMission() {
        missionService.createSeeded(DemoData.METALPRO, 145, "Opérateurs de production — Valenciennes",
                "Usine de Valenciennes", null, DemoData.EMAIL_0145, DemoData.MARC);
    }

    private void advance(Mission m, Phase target) {
        while (m.getPhase() != target) {
            completePhase(m);
            missionService.finalizePhase(m.getId(), m.getPhase());
        }
    }

    /** Real AI extraction (mock profile), then the flagged fields are confirmed as the AI proposed them. */
    private void completeIntake(Mission m) {
        intake.extract(m.getId()).fields().stream()
                .filter(IntakeService.FieldView::needsReview)
                .forEach(f -> intake.confirm(m.getId(), f.name(), new IntakeService.ConfirmRequest(null)));
    }

    private void completePhase(Mission m) {
        switch (m.getPhase()) {
            case INTAKE -> completeIntake(m);
            case SOURCING -> artifacts.upsert(m.getId(), Phase.SOURCING, ArtifactService.SHORTLIST, "SL-" + m.getNumber(), "READY", Map.of());
            case CONTRACTS -> artifacts.upsert(m.getId(), Phase.CONTRACTS, ArtifactService.CONTRACT, "CT-" + m.getNumber() + "-01", "SIGNED", Map.of());
            default -> throw new IllegalStateException("Seeder cannot complete phase " + m.getPhase());
        }
    }
}
