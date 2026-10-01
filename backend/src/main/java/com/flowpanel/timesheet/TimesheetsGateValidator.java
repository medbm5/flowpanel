package com.flowpanel.timesheet;

import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TimesheetsGateValidator implements GateValidator {

    private final ArtifactService artifacts;

    public TimesheetsGateValidator(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public Phase phase() {
        return Phase.TIMESHEETS;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        var found = artifacts.byType(mission.getId(), ArtifactService.TIMESHEETS);
        boolean passed = !found.isEmpty() && found.stream().allMatch(a -> "APPROVED".equals(a.getStatus()));
        return List.of(GateCheck.of("timesheets-approved", "Timesheets approved", passed, "Check and approve timesheets"));
    }
}
