package com.flowpanel.sourcing;

import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionPositions.Positions;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Sourcing is done when the order was published and every position is filled. */
@Component
public class SourcingGateValidator implements GateValidator {

    private final SourcingService sourcing;

    public SourcingGateValidator(@Lazy SourcingService sourcing) {
        this.sourcing = sourcing;
    }

    @Override
    public Phase phase() {
        return Phase.SOURCING;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        boolean published = sourcing.isPublished(mission.getId());
        Positions p = sourcing.positions(mission);
        int total = p.total() == null ? 0 : p.total();
        boolean filled = total > 0 && p.filled() >= total;
        return List.of(
                GateCheck.of("published", "Order published to panel suppliers", published,
                        "Publish the order to panel suppliers"),
                GateCheck.of("positions-filled", "Positions filled (" + p.filled() + "/" + total + ")", filled,
                        "Select " + Math.max(0, total - p.filled()) + " more candidate" + (total - p.filled() == 1 ? "" : "s")));
    }
}
