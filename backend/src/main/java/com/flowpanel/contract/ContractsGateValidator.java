package com.flowpanel.contract;

import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContractsGateValidator implements GateValidator {

    private final ArtifactService artifacts;

    public ContractsGateValidator(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public Phase phase() {
        return Phase.CONTRACTS;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        var found = artifacts.byType(mission.getId(), ArtifactService.CONTRACT);
        boolean passed = !found.isEmpty() && found.stream().allMatch(a -> "SIGNED".equals(a.getStatus()));
        return List.of(GateCheck.of("contracts-signed", "All contracts signed", passed, "Generate and sign contracts"));
    }
}
