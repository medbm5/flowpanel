package com.flowpanel.mission.gate;

import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GateRegistry {

    private final Map<Phase, GateValidator> validators = new EnumMap<>(Phase.class);

    public GateRegistry(List<GateValidator> all) {
        for (GateValidator v : all) {
            if (validators.put(v.phase(), v) != null) {
                throw new IllegalStateException("Two gate validators for phase " + v.phase());
            }
        }
        for (Phase p : Phase.values()) {
            if (!validators.containsKey(p)) {
                throw new IllegalStateException("No gate validator for phase " + p);
            }
        }
    }

    public GateEvaluation evaluate(Mission mission) {
        return new GateEvaluation(mission.getPhase(), validators.get(mission.getPhase()).check(mission));
    }
}
