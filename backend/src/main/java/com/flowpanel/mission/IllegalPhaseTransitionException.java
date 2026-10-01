package com.flowpanel.mission;

import com.flowpanel.common.ConflictException;
import java.util.Map;

public class IllegalPhaseTransitionException extends ConflictException {

    public IllegalPhaseTransitionException(Phase from, Phase to) {
        super("Transition " + from + " -> " + to + " is not allowed", Map.of("from", from.name(), "to", to.name()));
    }
}
