package com.flowpanel.intake;

import java.util.List;

/**
 * One field of the order draft.
 *
 * @param aiValue     value proposed by the AI (kept for the correction metric and the audit trail)
 * @param needsReview validation failed or confidence below threshold, and no person confirmed it yet
 * @param corrected   a person changed the AI value
 */
public record DraftField(String name, String label, String value, String aiValue, double confidence,
                         List<String> errors, boolean needsReview, boolean confirmed, boolean corrected) {
}
