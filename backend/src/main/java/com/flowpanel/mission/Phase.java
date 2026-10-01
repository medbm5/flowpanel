package com.flowpanel.mission;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Mission phases with an explicit allowed-transitions map. Phases are strictly sequential. */
public enum Phase {
    INTAKE("Intake"),
    SOURCING("Sourcing"),
    CONTRACTS("Contracts"),
    TIMESHEETS("Timesheets"),
    INVOICE("Invoice"),
    CLOSED("Closed");

    private static final Map<Phase, Set<Phase>> ALLOWED = new EnumMap<>(Phase.class);

    static {
        ALLOWED.put(INTAKE, EnumSet.of(SOURCING));
        ALLOWED.put(SOURCING, EnumSet.of(CONTRACTS));
        ALLOWED.put(CONTRACTS, EnumSet.of(TIMESHEETS));
        ALLOWED.put(TIMESHEETS, EnumSet.of(INVOICE));
        ALLOWED.put(INVOICE, EnumSet.of(CLOSED));
        ALLOWED.put(CLOSED, EnumSet.noneOf(Phase.class));
    }

    private final String label;

    Phase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canTransitionTo(Phase target) {
        return ALLOWED.get(this).contains(target);
    }

    public Set<Phase> allowedTargets() {
        return EnumSet.copyOf(ALLOWED.get(this));
    }

    /** The phase reached by finalizing this one; empty for CLOSED. */
    public Optional<Phase> next() {
        return ALLOWED.get(this).stream().findFirst();
    }

    public boolean isBefore(Phase other) {
        return ordinal() < other.ordinal();
    }
}
