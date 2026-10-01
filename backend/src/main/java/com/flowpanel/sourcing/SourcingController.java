package com.flowpanel.sourcing;

import com.flowpanel.sourcing.SourcingService.SourcingView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "sourcing")
public class SourcingController {

    private final SourcingService service;

    public SourcingController(SourcingService service) {
        this.service = service;
    }

    @GetMapping("/missions/{id}/sourcing")
    public SourcingView get(@PathVariable Long id) {
        return service.view(id);
    }

    @PostMapping("/missions/{id}/sourcing/publish")
    public SourcingView publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PostMapping("/missions/{id}/sourcing/select/{candidateId}")
    public SourcingView select(@PathVariable Long id, @PathVariable Long candidateId) {
        return service.select(id, candidateId);
    }

    @DeleteMapping("/missions/{id}/sourcing/select/{candidateId}")
    public SourcingView unselect(@PathVariable Long id, @PathVariable Long candidateId) {
        return service.unselect(id, candidateId);
    }
}
