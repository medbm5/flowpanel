package com.flowpanel.mission;

import com.flowpanel.mission.gate.GateCheck;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MissionDtos {

    private MissionDtos() {
    }

    /** Create a mission from a request template, or from raw email text. Exactly one of the two. */
    public record CreateMissionRequest(String templateCode, @Size(max = 20000) String emailText, String title) {
    }

    public enum PhaseState { DONE, ACTIVE, LOCKED }

    public record PhaseStep(Phase phase, String label, PhaseState state, Instant finalizedAt, String finalizedBy) {
    }

    public record GateView(Phase phase, List<GateCheck> checks, boolean ready) {
    }

    public record ArtifactView(Long id, Phase phase, String type, String ref, String status, Map<String, Object> payload,
                               Instant updatedAt) {
    }

    public record PositionsView(int filled, Integer total) {
    }

    public record MissionSummary(Long id, String ref, String title, String site, Phase phase, List<PhaseStep> phases,
                                 PositionsView positions, String nextAction, boolean needsReview, Instant createdAt,
                                 Instant updatedAt) {
    }

    public record MissionDetail(Long id, String ref, String title, String site, Phase phase, boolean closed,
                                boolean needsReview, String nextAction, GateView gate, List<PhaseStep> phases,
                                List<ArtifactView> artifacts, PositionsView positions, String sourceEmail,
                                String templateCode, Instant createdAt, Instant closedAt, Map<String, Object> summary) {
    }

    public record TemplateView(String code, String title, String site, String description, String emailText) {
    }

    public record AuditView(Long id, String actorKind, String actorName, String action, String summary,
                            Map<String, Object> details, Instant createdAt) {
    }
}
