package com.flowpanel.invoice;

import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionEvents;
import com.flowpanel.mission.MissionService;
import com.flowpanel.mission.Phase;
import com.flowpanel.sourcing.SourcingService;
import com.flowpanel.timesheet.Timesheet;
import com.flowpanel.timesheet.TimesheetService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes the closing summary when a mission enters CLOSED. All figures are deterministic. */
@Service
@Transactional
public class ClosingService {

    private final MissionService missions;
    private final SourcingService sourcing;
    private final TimesheetService timesheets;
    private final InvoiceService invoices;
    private final ArtifactService artifacts;
    private final JdbcTemplate jdbc;

    public ClosingService(MissionService missions, SourcingService sourcing, TimesheetService timesheets,
                          InvoiceService invoices, ArtifactService artifacts, JdbcTemplate jdbc) {
        this.missions = missions;
        this.sourcing = sourcing;
        this.timesheets = timesheets;
        this.invoices = invoices;
        this.artifacts = artifacts;
        this.jdbc = jdbc;
    }

    @EventListener
    public void onPhaseEntered(MissionEvents.PhaseEntered event) {
        if (event.phase() != Phase.CLOSED) {
            return;
        }
        Mission m = event.mission();
        Map<String, Object> summary = compute(m);
        m.setSummary(summary);
        artifacts.upsert(m.getId(), Phase.CLOSED, ArtifactService.SUMMARY, "SUM-" + m.getNumber(), "FINAL", summary);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long missionId) {
        Mission m = missions.requireReached(missionId, Phase.CLOSED);
        return m.getSummary() == null ? compute(m) : m.getSummary();
    }

    Map<String, Object> compute(Mission m) {
        int placed = sourcing.placementsOf(m.getId()).size();
        BigDecimal hours = timesheets.forMission(m.getId()).stream().map(Timesheet::getApprovedHours)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Invoice> all = invoices.forMission(m.getId());
        BigDecimal approvedAmount = all.stream().map(InvoiceService::payable).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avoided = all.stream().map(Invoice::getCreditNoteAmount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Integer aiSteps = jdbc.queryForObject("select count(*) from ai_call where mission_id = ? and status = 'OK' and kind <> 'EMBEDDING'",
                Integer.class, m.getId());
        Integer decisions = jdbc.queryForObject("select count(*) from audit_event where mission_id = ? and actor_kind = 'HUMAN'",
                Integer.class, m.getId());
        Integer corrected = jdbc.queryForObject("select count(*) from audit_event where mission_id = ? and action = 'intake.field.corrected'",
                Integer.class, m.getId());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("workersPlaced", placed);
        summary.put("hoursApproved", hours);
        summary.put("amountApproved", approvedAmount);
        summary.put("overbillingAvoided", avoided);
        summary.put("aiStepsReviewed", aiSteps);
        summary.put("humanDecisions", decisions);
        summary.put("aiFieldsCorrected", corrected);
        return summary;
    }
}
