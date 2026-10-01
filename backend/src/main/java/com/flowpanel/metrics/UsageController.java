package com.flowpanel.metrics;

import com.flowpanel.metrics.UsageService.UsageOverview;
import com.flowpanel.metrics.UsageService.UsageUserDetail;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin")
public class UsageController {

    private final UsageService service;

    public UsageController(UsageService service) {
        this.service = service;
    }

    /** Usage per user over the last {@code days} (1, 7, 30 or 90). */
    @GetMapping("/admin/usage/users")
    public UsageOverview users(@RequestParam(defaultValue = "30") int days) {
        return service.overview(days);
    }

    /** Detail of one user; omit {@code userId} for calls made without a user (Unattributed). */
    @GetMapping("/admin/usage/detail")
    public UsageUserDetail detail(@RequestParam(required = false) Long userId, @RequestParam(defaultValue = "30") int days) {
        return service.detail(userId, days);
    }
}
