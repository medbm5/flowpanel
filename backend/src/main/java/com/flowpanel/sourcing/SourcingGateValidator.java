package com.flowpanel.sourcing;

import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SourcingGateValidator implements GateValidator {

    private final ArtifactService artifacts;

    public SourcingGateValidator(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public Phase phase() {
        return Phase.SOURCING;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        var found = artifacts.byType(mission.getId(), ArtifactService.SHORTLIST);
        boolean passed = !found.isEmpty() && found.stream().allMatch(a -> "READY".equals(a.getStatus()));
        return List.of(GateCheck.of("shortlist-ready", "Shortlist ready", passed, "Publish the order to panel suppliers"));
    }
}
