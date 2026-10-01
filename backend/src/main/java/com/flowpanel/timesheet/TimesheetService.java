package com.flowpanel.timesheet;

import com.flowpanel.ai.AiGateway;
import com.flowpanel.ai.AiPrompt;
import com.flowpanel.ai.AiResult;
import com.flowpanel.audit.ActorKind;
import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.BadRequestException;
import com.flowpanel.common.ConflictException;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.contract.Contract;
import com.flowpanel.contract.ContractService;
import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionEvents;
import com.flowpanel.mission.MissionService;
import com.flowpanel.mission.Phase;
import com.flowpanel.sourcing.Worker;
import com.flowpanel.sourcing.WorkerRepository;
import com.flowpanel.tenant.TenantService;
import com.flowpanel.timesheet.TimesheetAnomaly.Resolution;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TimesheetService {

    public static final String FEATURE = "timesheet.explain";

    static final String SYSTEM_PROMPT = """
            You explain a timesheet anomaly to a staffing buyer in 2 short sentences, in English, using only the daily
            breakdown and the rule given. Say what differs from the contract and what the two options mean (approve the
            overtime, or return the sheet to the supplier). Do not recommend an option and do not invent numbers.""";

    /** Seeded anomaly: the first full week of the first contract is submitted with 41 h for 35 h contracted. */
    static final List<BigDecimal> SEEDED_WEEK = List.of(new BigDecimal("8.00"), new BigDecimal("8.00"), new BigDecimal("8.00"),
            new BigDecimal("8.50"), new BigDecimal("8.50"), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));

    public record AnomalyView(Long id, Long timesheetId, String workerName, LocalDate weekStart, String ruleId,
                              String message, BigDecimal expectedHours, BigDecimal actualHours, List<Integer> flaggedDays,
                              String aiExplanation, String status, Resolution resolution, Instant resolvedAt) {
    }

    public record TimesheetView(Long id, Long contractId, String contractRef, Long workerId, String workerName,
                                String supplierName, LocalDate weekStart, List<BigDecimal> dailyHours, BigDecimal total,
                                BigDecimal contractedHours, String status, BigDecimal approvedHours, List<Integer> flaggedDays) {
    }

    public record WorkerHours(Long workerId, String workerName, BigDecimal submitted, BigDecimal approved) {
    }

    public record TimesheetsView(Long missionId, boolean readOnly, boolean checked, Instant checkedAt, boolean approved,
                                 List<TimesheetView> timesheets, List<AnomalyView> anomalies, List<WorkerHours> totals) {
    }

    public record ResolveRequest(Resolution resolution) {
    }

    private final MissionService missions;
    private final ContractService contracts;
    private final TimesheetRepository timesheets;
    private final TimesheetAnomalyRepository anomalies;
    private final WorkerRepository workers;
    private final TenantService tenants;
    private final AiGateway gateway;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final RequestContext context;
    private final JdbcTemplate jdbc;

    public TimesheetService(MissionService missions, ContractService contracts, TimesheetRepository timesheets,
                            TimesheetAnomalyRepository anomalies, WorkerRepository workers, TenantService tenants,
                            AiGateway gateway, ArtifactService artifacts, AuditService audit, RequestContext context,
                            JdbcTemplate jdbc) {
        this.missions = missions;
        this.contracts = contracts;
        this.timesheets = timesheets;
        this.anomalies = anomalies;
        this.workers = workers;
        this.tenants = tenants;
        this.gateway = gateway;
        this.artifacts = artifacts;
        this.audit = audit;
        this.context = context;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ phase opening

    /** When the phase opens, the suppliers' weekly timesheets arrive (simulated), one per contract and week. */
    @EventListener
    public void onPhaseEntered(MissionEvents.PhaseEntered event) {
        if (event.phase() != Phase.TIMESHEETS) {
            return;
        }
        Mission m = event.mission();
        boolean seeded = false;
        for (Contract c : contracts.forMission(m.getId())) {
            BigDecimal daily = TimesheetRulesEngine.scheduledDaily(c.getWeeklyHours());
            LocalDate week = c.getStartDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            while (!week.isAfter(c.getEndDate())) {
                List<BigDecimal> pattern = TimesheetRulesEngine.contractedPattern(week, c.getStartDate(), c.getEndDate(), daily);
                BigDecimal contracted = TimesheetRulesEngine.contractedFor(pattern);
                boolean fullWeek = contracted.compareTo(daily.multiply(BigDecimal.valueOf(5))) == 0;
                List<BigDecimal> submitted = pattern;
                if (!seeded && fullWeek && !c.isOvertimeAllowed()) {
                    submitted = SEEDED_WEEK;
                    seeded = true;
                }
                timesheets.save(new Timesheet(m.getId(), c.getId(), c.getWorkerId(), c.getSupplierId(), week, submitted, contracted));
                week = week.plusWeeks(1);
            }
        }
        audit.record(ActorKind.SYSTEM, m.getTenantId(), m.getId(), "timesheets.submitted",
                "Suppliers submitted " + timesheets.findByMissionIdOrderByWeekStartAscWorkerIdAsc(m.getId()).size()
                        + " weekly timesheets", Map.of());
        syncArtifact(m, "SUBMITTED");
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public TimesheetsView view(Long missionId) {
        Mission m = missions.requireReached(missionId, Phase.TIMESHEETS);
        return toView(m);
    }

    @Transactional(readOnly = true)
    public boolean checked(Long missionId) {
        return checkedAt(missionId).isPresent();
    }

    @Transactional(readOnly = true)
    public List<Timesheet> forMission(Long missionId) {
        return timesheets.findByMissionIdOrderByWeekStartAscWorkerIdAsc(missionId);
    }

    @Transactional(readOnly = true)
    public long openAnomalies(Long missionId) {
        return anomalies.findByMissionIdOrderByIdAsc(missionId).stream().filter(TimesheetAnomaly::isOpen).count();
    }

    /** Approved hours per contract (only once approved). Used by the three-way match. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> approvedHoursByContract(Long missionId) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Timesheet t : forMission(missionId)) {
            if (t.getApprovedHours() != null) {
                result.merge(t.getContractId(), t.getApprovedHours(), BigDecimal::add);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ commands

    /** Runs the rules on every sheet; each new anomaly gets a short AI explanation of the daily breakdown. */
    public TimesheetsView check(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.TIMESHEETS);
        List<TimesheetAnomaly> existing = anomalies.findByMissionIdOrderByIdAsc(m.getId());
        anomalies.deleteAll(existing.stream().filter(TimesheetAnomaly::isOpen).toList());
        anomalies.flush();
        int found = 0;
        for (Timesheet t : forMission(m.getId())) {
            boolean handled = existing.stream().anyMatch(a -> a.getTimesheetId().equals(t.getId()) && !a.isOpen());
            if (handled || "APPROVED".equals(t.getStatus())) {
                continue;
            }
            Contract c = contract(t);
            var week = new TimesheetRulesEngine.Week(t.getWeekStart(), t.getDailyHours(), t.getContractedHours(),
                    TimesheetRulesEngine.scheduledDaily(c.getWeeklyHours()), c.isOvertimeAllowed());
            for (TimesheetRulesEngine.Anomaly a : TimesheetRulesEngine.check(week)) {
                TimesheetAnomaly saved = anomalies.save(new TimesheetAnomaly(m.getId(), t.getId(), a));
                explain(m, t, saved);
                found++;
            }
        }
        jdbc.update("insert into timesheet_check_run (mission_id, checked_at, anomalies) values (?, now(), ?) "
                + "on conflict (mission_id) do update set checked_at = now(), anomalies = excluded.anomalies", m.getId(), found);
        audit.human(m.getId(), "timesheets.checked", "Ran timesheet checks: " + found + " anomal" + (found == 1 ? "y" : "ies"),
                Map.of("anomalies", found));
        m.touch();
        return toView(m);
    }

    public TimesheetsView resolve(Long anomalyId, ResolveRequest request) {
        if (request == null || request.resolution() == null) {
            throw new BadRequestException("resolution is required (APPROVE_OVERTIME or RETURN_TO_SUPPLIER)");
        }
        TimesheetAnomaly a = anomalies.findById(anomalyId).orElseThrow(() -> new NotFoundException("Anomaly", anomalyId));
        Mission m = missions.requireCurrentPhase(a.getMissionId(), Phase.TIMESHEETS);
        if (!a.isOpen()) {
            throw new ConflictException("Anomaly " + anomalyId + " is already resolved");
        }
        Timesheet t = timesheets.findById(a.getTimesheetId()).orElseThrow();
        Contract c = contract(t);
        String worker = workerName(t.getWorkerId());
        if (request.resolution() == Resolution.RETURN_TO_SUPPLIER) {
            List<BigDecimal> corrected = TimesheetRulesEngine.contractedPattern(t.getWeekStart(), c.getStartDate(), c.getEndDate(),
                    TimesheetRulesEngine.scheduledDaily(c.getWeeklyHours()));
            BigDecimal before = t.total();
            t.correct(corrected);
            audit.record(ActorKind.SYSTEM, m.getTenantId(), m.getId(), "timesheet.corrected",
                    tenants.supplierName(t.getSupplierId()) + " sent a corrected timesheet for " + worker + ": "
                            + TimesheetRulesEngine.fmt(before) + " h → " + TimesheetRulesEngine.fmt(t.total()) + " h",
                    Map.of("timesheetId", t.getId()));
        }
        // Other open anomalies of the same sheet are covered by the same decision.
        for (TimesheetAnomaly other : anomalies.findByTimesheetId(t.getId())) {
            if (other.isOpen()) {
                other.resolve(request.resolution(), context.current().userId());
            }
        }
        audit.human(m.getId(), "timesheet.anomaly.resolved", (request.resolution() == Resolution.APPROVE_OVERTIME
                        ? "Approved overtime for " : "Returned to supplier the timesheet of ") + worker + " (week of " + t.getWeekStart() + ")",
                Map.of("anomalyId", a.getId(), "resolution", request.resolution().name()));
        m.touch();
        return toView(m);
    }

    /** Approves every sheet; approved hours are computed here, in Java, from the (possibly corrected) daily hours. */
    public TimesheetsView approve(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.TIMESHEETS);
        if (!checked(m.getId())) {
            throw new ConflictException("Run the timesheet checks before approving");
        }
        long open = openAnomalies(m.getId());
        if (open > 0) {
            throw new ConflictException(open + " anomal" + (open == 1 ? "y is" : "ies are") + " still open");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Timesheet t : forMission(m.getId())) {
            BigDecimal hours = TimesheetRulesEngine.approvedHours(t.getDailyHours(), null, false);
            t.approve(hours);
            total = total.add(hours);
        }
        audit.human(m.getId(), "timesheets.approved", "Approved timesheets: " + TimesheetRulesEngine.fmt(total) + " h",
                Map.of("approvedHours", total));
        m.touch();
        syncArtifact(m, "APPROVED");
        return toView(m);
    }

    // ------------------------------------------------------------------ internals

    private void explain(Mission m, Timesheet t, TimesheetAnomaly a) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("rule", a.getRuleId());
        facts.put("message", a.getMessage());
        facts.put("weekStart", t.getWeekStart().toString());
        Map<String, Object> days = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            days.put(TimesheetRulesEngine.dayName(i), t.getDailyHours().get(i).stripTrailingZeros().toPlainString());
        }
        facts.put("days", days);
        facts.put("contractedWeekly", t.getContractedHours().stripTrailingZeros().toPlainString());
        facts.put("scheduledDaily", TimesheetRulesEngine.scheduledDaily(contract(t).getWeeklyHours()).stripTrailingZeros().toPlainString());
        facts.put("submittedTotal", t.total().stripTrailingZeros().toPlainString());
        facts.put("overtimeAllowed", contract(t).isOvertimeAllowed());
        String user = "Anomaly: " + a.getMessage() + "\nWeek of " + t.getWeekStart() + ", daily hours: " + days
                + "\nContracted: " + facts.get("contractedWeekly") + " h/week (" + facts.get("scheduledDaily") + " h/day)";
        AiResult<String> result = gateway.text(AiPrompt.of(FEATURE, m.getId(), SYSTEM_PROMPT, user).withFacts(facts).withMaxTokens(120));
        a.explain(result.value().strip(), result.aiCallId());
    }

    private Contract contract(Timesheet t) {
        return contracts.forMission(t.getMissionId()).stream().filter(c -> c.getId().equals(t.getContractId())).findFirst()
                .orElseThrow();
    }

    private String workerName(Long workerId) {
        return workers.findById(workerId).map(Worker::fullName).orElse("");
    }

    private Optional<Instant> checkedAt(Long missionId) {
        return jdbc.query("select checked_at from timesheet_check_run where mission_id = ?",
                rs -> rs.next() ? Optional.of(rs.getTimestamp(1).toInstant()) : Optional.<Instant>empty(), missionId);
    }

    private void syncArtifact(Mission m, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        totals(m.getId()).forEach(w -> rows.add(Map.of("worker", w.workerName(), "submitted", w.submitted(),
                "approved", w.approved() == null ? "" : w.approved())));
        payload.put("workers", rows);
        artifacts.upsert(m.getId(), Phase.TIMESHEETS, ArtifactService.TIMESHEETS, "TS-" + m.getNumber(), status, payload);
    }

    private List<WorkerHours> totals(Long missionId) {
        Map<Long, BigDecimal> submitted = new LinkedHashMap<>();
        Map<Long, BigDecimal> approved = new LinkedHashMap<>();
        for (Timesheet t : forMission(missionId)) {
            submitted.merge(t.getWorkerId(), t.total(), BigDecimal::add);
            if (t.getApprovedHours() != null) {
                approved.merge(t.getWorkerId(), t.getApprovedHours(), BigDecimal::add);
            }
        }
        return submitted.entrySet().stream()
                .map(e -> new WorkerHours(e.getKey(), workerName(e.getKey()), e.getValue(), approved.get(e.getKey())))
                .toList();
    }

    private TimesheetsView toView(Mission m) {
        List<Timesheet> sheets = forMission(m.getId());
        List<TimesheetAnomaly> all = anomalies.findByMissionIdOrderByIdAsc(m.getId());
        Map<Long, String> refs = new LinkedHashMap<>();
        contracts.forMission(m.getId()).forEach(c -> refs.put(c.getId(), c.getRef()));
        List<TimesheetView> views = sheets.stream().map(t -> new TimesheetView(t.getId(), t.getContractId(),
                refs.get(t.getContractId()), t.getWorkerId(), workerName(t.getWorkerId()), tenants.supplierName(t.getSupplierId()),
                t.getWeekStart(), t.getDailyHours(), t.total(), t.getContractedHours(), t.getStatus(), t.getApprovedHours(),
                all.stream().filter(a -> a.getTimesheetId().equals(t.getId()) && a.isOpen())
                        .flatMap(a -> a.getFlaggedDays().stream()).distinct().sorted().toList())).toList();
        List<AnomalyView> anomalyViews = all.stream().map(a -> {
            Timesheet t = sheets.stream().filter(s -> s.getId().equals(a.getTimesheetId())).findFirst().orElseThrow();
            return new AnomalyView(a.getId(), t.getId(), workerName(t.getWorkerId()), t.getWeekStart(), a.getRuleId(),
                    a.getMessage(), a.getExpectedHours(), a.getActualHours(), a.getFlaggedDays(), a.getExplanation(),
                    a.getStatus(), a.getResolution(), a.getResolvedAt());
        }).toList();
        boolean approved = !sheets.isEmpty() && sheets.stream().allMatch(t -> "APPROVED".equals(t.getStatus()));
        Optional<Instant> checkedAt = checkedAt(m.getId());
        return new TimesheetsView(m.getId(), m.getPhase() != Phase.TIMESHEETS, checkedAt.isPresent(), checkedAt.orElse(null),
                approved, views, anomalyViews, totals(m.getId()));
    }
}
