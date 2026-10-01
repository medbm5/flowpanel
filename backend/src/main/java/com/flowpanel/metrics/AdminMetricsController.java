package com.flowpanel.metrics;

import com.flowpanel.metrics.AdminMetricsService.EvalRun;
import com.flowpanel.metrics.AdminMetricsService.EvalRunRequest;
import com.flowpanel.metrics.AdminMetricsService.Overview;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin")
public class AdminMetricsController {

    private final AdminMetricsService service;

    public AdminMetricsController(AdminMetricsService service) {
        this.service = service;
    }

    @GetMapping("/admin/metrics/overview")
    public Overview overview() {
        return service.overview();
    }

    @GetMapping("/admin/metrics/evals")
    public List<EvalRun> evals(@RequestParam(defaultValue = "50") int limit) {
        return service.evalRuns(limit);
    }

    @PostMapping("/admin/evals/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public EvalRun recordRun(@RequestBody EvalRunRequest request) {
        return service.recordEvalRun(request);
    }
}
