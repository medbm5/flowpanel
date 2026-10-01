package com.flowpanel.mission;

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
@Table(name = "mission")
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long tenantId;
    private int number;
    private String ref;
    private String title;
    private String site;
    @Enumerated(EnumType.STRING)
    private Phase phase;
    private String templateCode;
    private String sourceEmail;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> summary;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    protected Mission() {
    }

    public Mission(Long tenantId, int number, String ref, String title, String site, String templateCode,
                   String sourceEmail, Long createdBy) {
        this.tenantId = tenantId;
        this.number = number;
        this.ref = ref;
        this.title = title;
        this.site = site;
        this.templateCode = templateCode;
        this.sourceEmail = sourceEmail;
        this.createdBy = createdBy;
        this.phase = Phase.INTAKE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Moves to the target phase; only transitions from the allowed-transitions map are accepted. */
    void transitionTo(Phase target) {
        if (!phase.canTransitionTo(target)) {
            throw new IllegalPhaseTransitionException(phase, target);
        }
        this.phase = target;
        this.updatedAt = Instant.now();
        if (target == Phase.CLOSED) {
            this.closedAt = this.updatedAt;
        }
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void rename(String title, String site) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (site != null && !site.isBlank()) {
            this.site = site;
        }
        touch();
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public int getNumber() {
        return number;
    }

    public String getRef() {
        return ref;
    }

    public String getTitle() {
        return title;
    }

    public String getSite() {
        return site;
    }

    public Phase getPhase() {
        return phase;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getSourceEmail() {
        return sourceEmail;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
