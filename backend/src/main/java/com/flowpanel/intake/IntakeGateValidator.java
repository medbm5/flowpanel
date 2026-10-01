package com.flowpanel.intake;

import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Intake is done when the order was extracted and no field is left to review. */
@Component
public class IntakeGateValidator implements GateValidator {

    private final IntakeDraftRepository drafts;

    public IntakeGateValidator(IntakeDraftRepository drafts) {
        this.drafts = drafts;
    }

    @Override
    public Phase phase() {
        return Phase.INTAKE;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        var draft = drafts.findById(mission.getId());
        long flagged = draft.map(d -> d.getFields().stream().filter(DraftField::needsReview).count()).orElse(0L);
        return List.of(
                GateCheck.of("order-extracted", "Order extracted from the email", draft.isPresent(),
                        "Extract the order from the email"),
                new GateCheck("fields-reviewed",
                        flagged == 0 ? "No field left to review" : flagged + " field(s) to review",
                        draft.isPresent() && flagged == 0,
                        "Review " + flagged + " flagged field" + (flagged == 1 ? "" : "s"), flagged > 0));
    }
}
