package com.flowpanel.demo;

import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.RequestContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin")
public class DemoController {

    public record ResetResult(int missions) {
    }

    private final DemoSeeder seeder;
    private final RequestContext context;
    private final AuditService audit;

    public DemoController(DemoSeeder seeder, RequestContext context, AuditService audit) {
        this.seeder = seeder;
        this.context = context;
        this.audit = audit;
    }

    /** Re-seeds the demo (admin only; also enforced by the security filter chain). */
    @PostMapping("/admin/demo/reset")
    public ResetResult reset() {
        context.requireAdmin();
        int count = seeder.reset();
        audit.human(null, "demo.reset", "Demo data reset", Map.of("missions", count));
        return new ResetResult(count);
    }
}
