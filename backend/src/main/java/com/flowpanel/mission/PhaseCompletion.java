package com.flowpanel.mission;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "phase_completion")
public class PhaseCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long missionId;
    @Enumerated(EnumType.STRING)
    private Phase phase;
    private Instant finalizedAt;
    private Long finalizedBy;

    protected PhaseCompletion() {
    }

    public PhaseCompletion(Long missionId, Phase phase, Long finalizedBy) {
        this.missionId = missionId;
        this.phase = phase;
        this.finalizedBy = finalizedBy;
        this.finalizedAt = Instant.now();
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

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public Long getFinalizedBy() {
        return finalizedBy;
    }
}
