package com.flowpanel.invoice;

import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InvoiceGateValidator implements GateValidator {

    private final ArtifactService artifacts;

    public InvoiceGateValidator(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public Phase phase() {
        return Phase.INVOICE;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        var found = artifacts.byType(mission.getId(), ArtifactService.INVOICE);
        boolean passed = !found.isEmpty() && found.stream().allMatch(a -> "APPROVED".equals(a.getStatus()));
        return List.of(GateCheck.of("invoice-approved", "Invoice approved", passed, "Receive and approve the invoice"));
    }
}
