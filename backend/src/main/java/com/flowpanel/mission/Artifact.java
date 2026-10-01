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
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A document produced by a phase (Order, Shortlist, Contract, Timesheet, Invoice, Summary...). */
@Entity
@Table(name = "artifact")
public class Artifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long missionId;
    @Enumerated(EnumType.STRING)
    private Phase phase;
    private String type;
    private String ref;
    private String status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
    private Instant createdAt;
    private Instant updatedAt;

    protected Artifact() {
    }

    public Artifact(Long missionId, Phase phase, String type, String ref, String status, Map<String, Object> payload) {
        this.missionId = missionId;
        this.phase = phase;
        this.type = type;
        this.ref = ref;
        this.status = status;
        this.payload = payload == null ? new HashMap<>() : new HashMap<>(payload);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String status, Map<String, Object> payload) {
        this.status = status;
        if (payload != null) {
            this.payload = new HashMap<>(payload);
        }
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMissionId() {
        return missionId;
    }

    public Phase getPhase() {
        return phase;
    }

    public String getType() {
        return type;
    }

    public String getRef() {
        return ref;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
