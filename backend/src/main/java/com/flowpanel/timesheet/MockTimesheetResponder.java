package com.flowpanel.timesheet;

import com.flowpanel.ai.AiModelClient.ChatCall;
import com.flowpanel.ai.mock.MockResponder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Mock profile answer for {@code timesheet.explain}, written from the daily breakdown in the facts. */
@Component
public class MockTimesheetResponder implements MockResponder {

    @Override
    public List<String> features() {
        return List.of(TimesheetService.FEATURE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String respond(ChatCall call, ToolRunner tools) {
        Map<String, Object> f = call.facts();
        Map<String, Object> days = (Map<String, Object>) f.getOrDefault("days", Map.of());
        BigDecimal daily = new BigDecimal(String.valueOf(f.getOrDefault("scheduledDaily", "0")));
        List<String> above = new ArrayList<>();
        days.forEach((day, hours) -> {
            if (new BigDecimal(String.valueOf(hours)).compareTo(daily) > 0) {
                above.add(day + " " + hours + " h");
            }
        });
        if ("CONTRACTED_HOURS".equals(f.get("rule"))) {
            BigDecimal extra = new BigDecimal(String.valueOf(f.get("submittedTotal")))
                    .subtract(new BigDecimal(String.valueOf(f.get("contractedWeekly"))));
            return "The sheet shows " + f.get("submittedTotal") + " h for " + f.get("contractedWeekly")
                    + " h contracted (+" + extra.stripTrailingZeros().toPlainString() + " h): " + String.join(", ", above)
                    + " exceed the " + daily.stripTrailingZeros().toPlainString() + " h daily schedule. "
                    + "No overtime is agreed in the contract, so either approve these hours as overtime or return the sheet "
                    + "to the supplier for correction.";
        }
        return "The week of " + f.get("weekStart") + " breaks a working-time rule (" + f.get("message") + "). "
                + "Approve it as an exception or return the sheet to the supplier for correction.";
    }
}
