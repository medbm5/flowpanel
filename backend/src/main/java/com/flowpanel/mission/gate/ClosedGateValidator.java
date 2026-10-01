package com.flowpanel.mission.gate;

import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import java.util.List;
import org.springframework.stereotype.Component;

/** CLOSED is terminal: nothing to check and nothing to finalize. */
@Component
public class ClosedGateValidator implements GateValidator {

    @Override
    public Phase phase() {
        return Phase.CLOSED;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        return List.of();
    }
}
