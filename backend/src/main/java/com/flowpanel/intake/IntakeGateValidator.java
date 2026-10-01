package com.flowpanel.intake;

import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IntakeGateValidator implements GateValidator {

    private final ArtifactService artifacts;

    public IntakeGateValidator(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public Phase phase() {
        return Phase.INTAKE;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        var found = artifacts.byType(mission.getId(), ArtifactService.ORDER);
        boolean passed = !found.isEmpty() && found.stream().allMatch(a -> "CONFIRMABLE".equals(a.getStatus()));
        return List.of(GateCheck.of("order-extracted", "Order draft extracted", passed, "Extract the order from the email"));
    }
}
