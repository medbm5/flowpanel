package com.flowpanel.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long tenantId;
    private Long supplierId;
    private Long missionId;
    @Enumerated(EnumType.STRING)
    private ActorKind actorKind;
    private Long actorUserId;
    private String actorName;
    private String action;
    private String summary;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(Long tenantId, Long supplierId, Long missionId, ActorKind actorKind, Long actorUserId,
                      String actorName, String action, String summary, Map<String, Object> details) {
        this.tenantId = tenantId;
        this.supplierId = supplierId;
        this.missionId = missionId;
        this.actorKind = actorKind;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.action = action;
        this.summary = summary;
        this.details = details == null ? Map.of() : details;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getMissionId() {
        return missionId;
    }

    public ActorKind getActorKind() {
        return actorKind;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getAction() {
        return action;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
