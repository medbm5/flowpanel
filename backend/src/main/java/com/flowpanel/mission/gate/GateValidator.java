package com.flowpanel.mission.gate;

import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import java.util.List;

/** Deterministic exit criteria of one phase. Exactly one implementation per phase. */
public interface GateValidator {

    Phase phase();

    List<GateCheck> check(Mission mission);
}
