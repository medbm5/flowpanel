package com.flowpanel.timesheet;

import com.flowpanel.timesheet.TimesheetService.ResolveRequest;
import com.flowpanel.timesheet.TimesheetService.TimesheetsView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "timesheets")
public class TimesheetController {

    private final TimesheetService service;

    public TimesheetController(TimesheetService service) {
        this.service = service;
    }

    @GetMapping("/missions/{id}/timesheets")
    public TimesheetsView get(@PathVariable Long id) {
        return service.view(id);
    }

    @PostMapping("/missions/{id}/timesheets/check")
    public TimesheetsView check(@PathVariable Long id) {
        return service.check(id);
    }

    @PostMapping("/anomalies/{anomalyId}/resolve")
    public TimesheetsView resolve(@PathVariable Long anomalyId, @RequestBody ResolveRequest request) {
        return service.resolve(anomalyId, request);
    }

    @PostMapping("/missions/{id}/timesheets/approve")
    public TimesheetsView approve(@PathVariable Long id) {
        return service.approve(id);
    }
}
