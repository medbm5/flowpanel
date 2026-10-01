package com.flowpanel.mission.gate;

import com.flowpanel.mission.Phase;
import java.util.List;

public record GateEvaluation(Phase phase, List<GateCheck> checks) {

    public boolean ready() {
        return phase != Phase.CLOSED && checks.stream().allMatch(GateCheck::passed);
    }

    public boolean needsReview() {
        return checks.stream().anyMatch(GateCheck::needsReview);
    }

    public List<String> unmetLabels() {
        return checks.stream().filter(c -> !c.passed()).map(GateCheck::label).toList();
    }

    public String nextAction() {
        if (phase == Phase.CLOSED) {
            return "Mission closed";
        }
        return checks.stream().filter(c -> !c.passed()).map(GateCheck::action).findFirst()
                .orElse("Finalize " + phase.label().toLowerCase());
    }
}
