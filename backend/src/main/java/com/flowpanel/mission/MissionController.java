package com.flowpanel.mission;

import com.flowpanel.common.BadRequestException;
import com.flowpanel.mission.MissionDtos.AuditView;
import com.flowpanel.mission.MissionDtos.CreateMissionRequest;
import com.flowpanel.mission.MissionDtos.MissionDetail;
import com.flowpanel.mission.MissionDtos.MissionSummary;
import com.flowpanel.mission.MissionDtos.TemplateView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "missions")
public class MissionController {

    private final MissionService service;

    public MissionController(MissionService service) {
        this.service = service;
    }

    @GetMapping("/missions")
    public List<MissionSummary> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping("/missions")
    @ResponseStatus(HttpStatus.CREATED)
    public MissionDetail create(@Valid @RequestBody CreateMissionRequest request) {
        return service.create(request);
    }

    @GetMapping("/missions/{id}")
    public MissionDetail get(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/missions/{id}/phases/{phase}/finalize")
    public MissionDetail finalizePhase(@PathVariable Long id, @PathVariable String phase) {
        return service.finalizePhase(id, parsePhase(phase));
    }

    @GetMapping("/missions/{id}/audit")
    public List<AuditView> audit(@PathVariable Long id) {
        return service.audit(id);
    }

    @GetMapping("/request-templates")
    public List<TemplateView> templates() {
        return service.templates();
    }

    static Phase parsePhase(String value) {
        try {
            return Phase.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown phase '" + value + "'");
        }
    }
}
