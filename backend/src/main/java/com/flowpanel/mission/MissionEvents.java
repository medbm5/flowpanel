package com.flowpanel.mission;

/** Synchronous, in-transaction lifecycle events. Feature modules listen to them to open or close their phase. */
public final class MissionEvents {

    private MissionEvents() {
    }

    /** Published after a phase's gate passed and its completion was recorded. */
    public record PhaseFinalized(Mission mission, Phase phase) {
    }

    /** Published after the mission moved into a new phase. */
    public record PhaseEntered(Mission mission, Phase phase) {
    }
}
