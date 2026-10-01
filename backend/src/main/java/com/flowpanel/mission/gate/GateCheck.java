package com.flowpanel.mission.gate;

/**
 * One item of a phase gate checklist.
 *
 * @param id          stable identifier (for tests and UI keys)
 * @param label       human-readable check, e.g. "No field left to review"
 * @param passed      whether the check currently passes
 * @param action      what the user should do next when the check fails
 * @param needsReview true when the failure is something a person must look at (flagged field, anomaly, mismatch...)
 */
public record GateCheck(String id, String label, boolean passed, String action, boolean needsReview) {

    public static GateCheck of(String id, String label, boolean passed, String action) {
        return new GateCheck(id, label, passed, action, false);
    }

    public static GateCheck review(String id, String label, boolean passed, String action) {
        return new GateCheck(id, label, passed, action, !passed);
    }
}
