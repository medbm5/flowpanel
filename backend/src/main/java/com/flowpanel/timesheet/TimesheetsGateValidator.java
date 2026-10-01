package com.flowpanel.timesheet;

import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Timesheets are done when the checks ran, every anomaly is resolved and the sheets are approved. */
@Component
public class TimesheetsGateValidator implements GateValidator {

    private final TimesheetService timesheets;

    public TimesheetsGateValidator(@Lazy TimesheetService timesheets) {
        this.timesheets = timesheets;
    }

    @Override
    public Phase phase() {
        return Phase.TIMESHEETS;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        boolean checked = timesheets.checked(mission.getId());
        long open = timesheets.openAnomalies(mission.getId());
        List<Timesheet> sheets = timesheets.forMission(mission.getId());
        boolean approved = !sheets.isEmpty() && sheets.stream().allMatch(t -> "APPROVED".equals(t.getStatus()));
        return List.of(
                GateCheck.of("checked", "Timesheet checks run", checked, "Run the timesheet checks"),
                new GateCheck("anomalies-resolved", open == 0 ? "All anomalies resolved" : open + " open anomal" + (open == 1 ? "y" : "ies"),
                        checked && open == 0, "Resolve the timesheet anomaly", open > 0),
                GateCheck.of("approved", "Timesheets approved", approved, "Approve the timesheets"));
    }
}
