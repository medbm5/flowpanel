package com.flowpanel.intake;

import com.flowpanel.intake.IntakeService.ConfirmRequest;
import com.flowpanel.intake.IntakeService.IntakeView;
import com.flowpanel.intake.IntakeService.PreviewView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "intake")
public class IntakeController {

    public record PreviewRequest(String emailText) {
    }

    private final IntakeService service;

    public IntakeController(IntakeService service) {
        this.service = service;
    }

    @GetMapping("/missions/{id}/intake")
    public IntakeView get(@PathVariable Long id) {
        return service.view(id);
    }

    @PostMapping("/missions/{id}/intake/extract")
    public IntakeView extract(@PathVariable Long id) {
        return service.extract(id);
    }

    @PostMapping("/missions/{id}/intake/fields/{field}/confirm")
    public IntakeView confirm(@PathVariable Long id, @PathVariable String field,
                              @RequestBody(required = false) ConfirmRequest request) {
        return service.confirm(id, field, request);
    }

    /** Stateless extraction, used by the eval runner. */
    @PostMapping("/intake/preview")
    public PreviewView preview(@RequestBody PreviewRequest request) {
        return service.preview(request.emailText());
    }
}
