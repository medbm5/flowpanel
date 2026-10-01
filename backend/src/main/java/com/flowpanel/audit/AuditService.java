package com.flowpanel.audit;

import com.flowpanel.auth.CurrentUser;
import com.flowpanel.auth.RequestContext;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes every AI suggestion and every human decision to {@code audit_event}. */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final RequestContext context;

    public AuditService(AuditEventRepository repository, RequestContext context) {
        this.repository = repository;
        this.context = context;
    }

    /** A decision taken by the current user. Joins the caller's transaction. */
    @Transactional
    public AuditEvent human(Long missionId, String action, String summary, Map<String, Object> details) {
        return write(ActorKind.HUMAN, null, missionId, action, summary, details);
    }

    /** A suggestion produced by the AI on behalf of the current user. */
    @Transactional
    public AuditEvent ai(Long missionId, String action, String summary, Map<String, Object> details) {
        return write(ActorKind.AI, null, missionId, action, summary, details);
    }

    @Transactional
    public AuditEvent system(Long missionId, String action, String summary, Map<String, Object> details) {
        return write(ActorKind.SYSTEM, null, missionId, action, summary, details);
    }

    /** Persists even if the caller's transaction rolls back (used for failed AI calls). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent aiIndependent(Long missionId, String action, String summary, Map<String, Object> details) {
        return write(ActorKind.AI, null, missionId, action, summary, details);
    }

    /** Explicit tenant, for events on a tenant's mission triggered by a supplier user or the system. */
    @Transactional
    public AuditEvent record(ActorKind kind, Long tenantId, Long missionId, String action, String summary,
                             Map<String, Object> details) {
        return write(kind, tenantId, missionId, action, summary, details);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> forMission(Long tenantId, Long missionId) {
        return repository.findByTenantIdAndMissionIdOrderByCreatedAtDescIdDesc(tenantId, missionId);
    }

    private AuditEvent write(ActorKind kind, Long explicitTenantId, Long missionId, String action, String summary,
                             Map<String, Object> details) {
        CurrentUser user = context.currentOptional().orElse(null);
        Long tenantId = explicitTenantId != null ? explicitTenantId : user == null ? null : user.tenantId();
        Long supplierId = user == null ? null : user.supplierId();
        String actorName = switch (kind) {
            case AI -> "Flowpanel AI";
            case SYSTEM -> "System";
            case HUMAN -> user == null ? "Unknown" : user.displayName();
        };
        Long actorUserId = user == null ? null : user.userId();
        return repository.save(new AuditEvent(tenantId, supplierId, missionId, kind, actorUserId, actorName, action,
                summary, details));
    }
}
