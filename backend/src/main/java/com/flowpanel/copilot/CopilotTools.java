package com.flowpanel.copilot;

import com.flowpanel.ai.AiTool;
import com.flowpanel.auth.CurrentUser;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.copilot.DocumentService.Passage;
import com.flowpanel.mission.MissionDtos.MissionDetail;
import com.flowpanel.mission.MissionRepository;
import com.flowpanel.mission.MissionService;
import com.flowpanel.sourcing.SupplierPortalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Functions the copilot may call. Every handler reads the caller's scope from {@link RequestContext}: a buyer only
 * sees their tenant, a supplier user only their supplier. Arguments chosen by the model can narrow a query, never
 * widen it.
 */
@Component
public class CopilotTools {

    public static final String LIST_MISSIONS = "listMissions";
    public static final String MISSION_STATUS = "getMissionStatus";
    public static final String SPEND = "getSpendBySupplier";
    public static final String ANOMALIES = "listOpenAnomalies";
    public static final String CONTRACTS_ENDING = "listContractsEndingBefore";
    public static final String SEARCH = "searchDocuments";

    /** Passages returned by searchDocuments during one request, numbered [1], [2]... for citations. */
    public static class PassageRegistry {
        private final List<Passage> passages = new ArrayList<>();

        synchronized int add(Passage p) {
            for (int i = 0; i < passages.size(); i++) {
                if (passages.get(i).chunkId().equals(p.chunkId())) {
                    return i + 1;
                }
            }
            passages.add(p);
            return passages.size();
        }

        public synchronized List<Passage> all() {
            return List.copyOf(passages);
        }
    }

    private final RequestContext context;
    private final JdbcTemplate jdbc;
    private final MissionService missions;
    private final MissionRepository missionRepository;
    private final SupplierPortalService supplierPortal;
    private final DocumentService documents;

    public CopilotTools(RequestContext context, JdbcTemplate jdbc, MissionService missions, MissionRepository missionRepository,
                        SupplierPortalService supplierPortal, DocumentService documents) {
        this.context = context;
        this.jdbc = jdbc;
        this.missions = missions;
        this.missionRepository = missionRepository;
        this.supplierPortal = supplierPortal;
        this.documents = documents;
    }

    public List<AiTool> tools(PassageRegistry registry) {
        return List.of(
                new AiTool(LIST_MISSIONS, "List the missions visible to the user, with phase and whether they need review. "
                        + "Optional status filter: all, open, review, closed.",
                        "{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"all\",\"open\",\"review\",\"closed\"]}},"
                                + "\"required\":[\"status\"],\"additionalProperties\":false}", this::listMissions),
                new AiTool(MISSION_STATUS, "Current phase, next action and gate checklist of one mission, by reference (e.g. ORD-2026-0142).",
                        "{\"type\":\"object\",\"properties\":{\"ref\":{\"type\":\"string\"}},\"required\":[\"ref\"],\"additionalProperties\":false}",
                        this::missionStatus),
                new AiTool(SPEND, "Spend and average contract hourly rate per staffing supplier, for the user's scope only.",
                        AiTool.NO_ARGS, args -> spendBySupplier()),
                new AiTool(ANOMALIES, "Open timesheet anomalies (worker, week, rule) in the user's scope.", AiTool.NO_ARGS,
                        args -> openAnomalies()),
                new AiTool(CONTRACTS_ENDING, "Contracts whose end date is before the given ISO date (yyyy-MM-dd).",
                        "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\"}},\"required\":[\"date\"],\"additionalProperties\":false}",
                        this::contractsEndingBefore),
                new AiTool(SEARCH, "Search the organization's policy documents. Returns numbered passages; cite them as [n].",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"],\"additionalProperties\":false}",
                        args -> search(args, registry)));
    }

    // ------------------------------------------------------------------ handlers

    Object listMissions(Map<String, Object> args) {
        CurrentUser user = context.current();
        String status = String.valueOf(args.getOrDefault("status", "all"));
        if (user.isBuyer()) {
            return missions.list(List.of("all", "open", "review", "closed").contains(status) ? status : "all").stream()
                    .map(m -> Map.of("ref", m.ref(), "title", m.title(), "phase", m.phase().name(), "needsReview", m.needsReview(),
                            "nextAction", m.nextAction()))
                    .toList();
        }
        if (user.isSupplier()) {
            return supplierPortal.orders().stream()
                    .map(o -> Map.of("ref", o.ref(), "client", o.client(), "title", o.title(), "phase", o.phase().name()))
                    .toList();
        }
        return List.of();
    }

    Object missionStatus(Map<String, Object> args) {
        CurrentUser user = context.current();
        String ref = String.valueOf(args.getOrDefault("ref", "")).strip();
        var mission = missionRepository.findByRef(ref);
        Map<String, Object> notFound = Map.of("error", "No mission " + ref + " in your scope");
        if (mission.isEmpty()) {
            return notFound;
        }
        // Scope is checked here, before calling services, so out-of-scope refs never raise inside the transaction.
        if (user.isBuyer() && mission.get().getTenantId().equals(user.tenantId())) {
            MissionDetail d = missions.detail(mission.get().getId());
            return Map.of("ref", d.ref(), "title", d.title(), "phase", d.phase().name(), "nextAction", d.nextAction(),
                    "needsReview", d.needsReview(),
                    "positions", d.positions().filled() + "/" + (d.positions().total() == null ? "?" : d.positions().total()),
                    "checks", d.gate().checks().stream().map(c -> (c.passed() ? "✓ " : "✗ ") + c.label()).toList());
        }
        if (user.isSupplier() && publishedTo(mission.get().getId(), user.supplierId())) {
            var o = supplierPortal.order(mission.get().getId()).order();
            return Map.of("ref", o.ref(), "client", o.client(), "phase", o.phase().name(), "myProposals", o.myProposals(),
                    "myPlacements", o.myPlacements());
        }
        return notFound;
    }

    private boolean publishedTo(Long missionId, Long supplierId) {
        Integer n = jdbc.queryForObject("select count(*) from mission_supplier where mission_id = ? and supplier_id = ?",
                Integer.class, missionId, supplierId);
        return n != null && n > 0;
    }

    Object spendBySupplier() {
        CurrentUser user = context.current();
        String scope;
        Object param;
        if (user.isBuyer()) {
            scope = "m.tenant_id = ?";
            param = user.tenantId();
        } else if (user.isSupplier()) {
            scope = "c.supplier_id = ?";
            param = user.supplierId();
        } else {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query("select s.name, count(c.id) as contracts, round(avg(c.hourly_rate), 2) as avg_rate, s.id as sid "
                + "from contract c join mission m on m.id = c.mission_id join supplier s on s.id = c.supplier_id "
                + "where " + scope + " group by s.id, s.name order by s.name", rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("supplier", rs.getString("name"));
                    row.put("contracts", rs.getInt("contracts"));
                    row.put("averageHourlyRate", rs.getBigDecimal("avg_rate"));
                    long sid = rs.getLong("sid");
                    String invoiceScope = user.isBuyer() ? "m.tenant_id = ?" : "i.supplier_id = ?";
                    Map<String, Object> totals = jdbc.queryForMap("select coalesce(sum((i.extraction->>'totalExclTax')::numeric), 0) as invoiced, "
                            + "coalesce(sum(i.credit_note_amount), 0) as credited from invoice i join mission m on m.id = i.mission_id "
                            + "where i.supplier_id = ? and " + invoiceScope, sid, param);
                    BigDecimal invoiced = (BigDecimal) totals.get("invoiced");
                    BigDecimal credited = (BigDecimal) totals.get("credited");
                    row.put("invoiced", invoiced);
                    row.put("creditNotes", credited);
                    row.put("spend", invoiced.subtract(credited));
                    rows.add(row);
                }, param);
        return rows;
    }

    Object openAnomalies() {
        CurrentUser user = context.current();
        String scope = user.isBuyer() ? "m.tenant_id = ?" : user.isSupplier() ? "t.supplier_id = ?" : null;
        if (scope == null) {
            return List.of();
        }
        return jdbc.query("select m.ref, w.first_name || ' ' || w.last_name as worker, t.week_start, a.message "
                + "from timesheet_anomaly a join timesheet t on t.id = a.timesheet_id join mission m on m.id = a.mission_id "
                + "join worker w on w.id = t.worker_id where a.status = 'OPEN' and " + scope + " order by a.id",
                (rs, i) -> Map.of("mission", rs.getString("ref"), "worker", rs.getString("worker"),
                        "weekStart", rs.getDate("week_start").toString(), "anomaly", rs.getString("message")),
                user.isBuyer() ? user.tenantId() : user.supplierId());
    }

    Object contractsEndingBefore(Map<String, Object> args) {
        CurrentUser user = context.current();
        LocalDate date;
        try {
            date = LocalDate.parse(String.valueOf(args.get("date")).strip());
        } catch (DateTimeParseException e) {
            return Map.of("error", "date must be yyyy-MM-dd");
        }
        String scope = user.isBuyer() ? "m.tenant_id = ?" : user.isSupplier() ? "c.supplier_id = ?" : null;
        if (scope == null) {
            return List.of();
        }
        return jdbc.query("select c.ref, m.ref as mission, w.first_name || ' ' || w.last_name as worker, c.end_date, c.status "
                + "from contract c join mission m on m.id = c.mission_id join worker w on w.id = c.worker_id "
                + "where c.end_date < ? and " + scope + " order by c.end_date, c.ref",
                (rs, i) -> Map.of("contract", rs.getString("ref"), "mission", rs.getString("mission"),
                        "worker", rs.getString("worker"), "endDate", rs.getDate("end_date").toString(), "status", rs.getString("status")),
                date, user.isBuyer() ? user.tenantId() : user.supplierId());
    }

    Object search(Map<String, Object> args, PassageRegistry registry) {
        CurrentUser user = context.current();
        if (!user.isBuyer()) {
            return Map.of("passages", List.of(), "note", "Document search is only available to client users");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Passage p : documents.search(user.tenantId(), String.valueOf(args.getOrDefault("query", "")), 4)) {
            int n = registry.add(p);
            out.add(Map.of("n", n, "document", p.documentTitle(), "section", p.heading() == null ? "" : p.heading(),
                    "text", p.content(), "score", Math.round(p.score() * 1000) / 1000.0));
        }
        return Map.of("passages", out);
    }
}
