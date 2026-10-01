package com.flowpanel.audit;

import com.flowpanel.auth.RequestContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit log explorer for the admin persona, across tenants. */
@RestController
@Tag(name = "admin")
public class AdminAuditController {

    public record AuditRow(Long id, String tenant, String missionRef, String actorKind, String actorName, String action,
                           String summary, Instant createdAt) {
    }

    private final JdbcTemplate jdbc;
    private final RequestContext context;

    public AdminAuditController(JdbcTemplate jdbc, RequestContext context) {
        this.jdbc = jdbc;
        this.context = context;
    }

    @GetMapping("/admin/audit")
    @Transactional(readOnly = true)
    public List<AuditRow> search(@RequestParam(required = false) String mission,
                                 @RequestParam(required = false) String actor,
                                 @RequestParam(required = false) String kind,
                                 @RequestParam(defaultValue = "100") int limit) {
        context.requireAdmin();
        StringBuilder sql = new StringBuilder("""
                select e.id, t.name as tenant, m.ref as mission_ref, e.actor_kind, e.actor_name, e.action, e.summary, e.created_at
                from audit_event e left join tenant t on t.id = e.tenant_id left join mission m on m.id = e.mission_id
                where 1 = 1
                """);
        List<Object> params = new ArrayList<>();
        if (mission != null && !mission.isBlank()) {
            sql.append(" and m.ref ilike ?");
            params.add("%" + mission.strip() + "%");
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" and e.actor_name ilike ?");
            params.add("%" + actor.strip() + "%");
        }
        if (kind != null && List.of("AI", "HUMAN", "SYSTEM").contains(kind)) {
            sql.append(" and e.actor_kind = ?");
            params.add(kind);
        }
        sql.append(" order by e.created_at desc, e.id desc limit ?");
        params.add(Math.min(Math.max(limit, 1), 500));
        return jdbc.query(sql.toString(), (rs, i) -> new AuditRow(rs.getLong("id"), rs.getString("tenant"),
                rs.getString("mission_ref"), rs.getString("actor_kind"), rs.getString("actor_name"), rs.getString("action"),
                rs.getString("summary"), rs.getTimestamp("created_at").toInstant()), params.toArray());
    }
}
