package com.flowpanel.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpanel.ai.AiProperties;
import com.flowpanel.ai.SpendGuard;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-wide AI monitoring for the admin persona, computed from {@code ai_call}, intake drafts and eval runs. */
@Service
@Transactional(readOnly = true)
public class AdminMetricsService {

    public record Summary(long calls, long errors, double errorRate, Long p50LatencyMs, Long p95LatencyMs, long tokens,
                          BigDecimal estimatedCostUsd, BigDecimal liveSpendTodayUsd, BigDecimal dailyBudgetUsd, String profile) {
    }

    public record TenantRow(String tenant, long calls, long tokens, BigDecimal estimatedCostUsd) {
    }

    public record FeatureRow(String feature, long calls, long errors, double errorRate, Long p50LatencyMs, Long p95LatencyMs,
                             long tokens, BigDecimal estimatedCostUsd) {
    }

    public record Corrections(long aiFields, long correctedFields, double share) {
    }

    public record Overview(Summary summary, List<TenantRow> byTenant, List<FeatureRow> byFeature, Corrections corrections) {
    }

    public record EvalRun(Long id, String gitSha, String profile, boolean passed, Map<String, Object> metrics,
                          Map<String, Object> thresholds, Instant createdAt) {
    }

    public record EvalRunRequest(String gitSha, String profile, Boolean passed, Map<String, Object> metrics,
                                 Map<String, Object> thresholds) {
    }

    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final AiProperties ai;
    private final SpendGuard spendGuard;
    private final ObjectMapper mapper;

    public AdminMetricsService(JdbcTemplate jdbc, RequestContext context, AiProperties ai, SpendGuard spendGuard,
                               ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.context = context;
        this.ai = ai;
        this.spendGuard = spendGuard;
        this.mapper = mapper;
    }

    public Overview overview() {
        context.requireAdmin();
        Summary summary = jdbc.queryForObject("""
                select count(*) as calls,
                       count(*) filter (where status in ('ERROR', 'INVALID_OUTPUT')) as errors,
                       percentile_cont(0.5) within group (order by latency_ms) filter (where status = 'OK') as p50,
                       percentile_cont(0.95) within group (order by latency_ms) filter (where status = 'OK') as p95,
                       coalesce(sum(input_tokens + output_tokens), 0) as tokens,
                       coalesce(sum(estimated_cost_usd), 0) as cost
                from ai_call
                """, (rs, i) -> {
            long calls = rs.getLong("calls");
            long errors = rs.getLong("errors");
            return new Summary(calls, errors, calls == 0 ? 0 : (double) errors / calls, toLong(rs.getObject("p50")),
                    toLong(rs.getObject("p95")), rs.getLong("tokens"), rs.getBigDecimal("cost"), spendGuard.spentToday(),
                    ai.dailyBudgetUsd(), ai.profileName());
        });
        List<TenantRow> byTenant = jdbc.query("""
                select coalesce(t.name, s.name || ' (supplier)', 'Platform') as tenant, count(*) as calls,
                       coalesce(sum(c.input_tokens + c.output_tokens), 0) as tokens, coalesce(sum(c.estimated_cost_usd), 0) as cost
                from ai_call c left join tenant t on t.id = c.tenant_id left join supplier s on s.id = c.supplier_id
                group by 1 order by cost desc, 1
                """, (rs, i) -> new TenantRow(rs.getString("tenant"), rs.getLong("calls"), rs.getLong("tokens"), rs.getBigDecimal("cost")));
        List<FeatureRow> byFeature = jdbc.query("""
                select feature, count(*) as calls,
                       count(*) filter (where status in ('ERROR', 'INVALID_OUTPUT')) as errors,
                       percentile_cont(0.5) within group (order by latency_ms) filter (where status = 'OK') as p50,
                       percentile_cont(0.95) within group (order by latency_ms) filter (where status = 'OK') as p95,
                       coalesce(sum(input_tokens + output_tokens), 0) as tokens, coalesce(sum(estimated_cost_usd), 0) as cost
                from ai_call where feature not like 'test.%' group by feature order by calls desc
                """, (rs, i) -> {
            long calls = rs.getLong("calls");
            long errors = rs.getLong("errors");
            return new FeatureRow(rs.getString("feature"), calls, errors, calls == 0 ? 0 : (double) errors / calls,
                    toLong(rs.getObject("p50")), toLong(rs.getObject("p95")), rs.getLong("tokens"), rs.getBigDecimal("cost"));
        });
        Corrections corrections = jdbc.queryForObject("""
                select count(*) as fields, count(*) filter (where (f ->> 'corrected')::boolean) as corrected
                from intake_draft d, jsonb_array_elements(d.fields) f
                """, (rs, i) -> {
            long fields = rs.getLong("fields");
            long corrected = rs.getLong("corrected");
            return new Corrections(fields, corrected, fields == 0 ? 0 : (double) corrected / fields);
        });
        return new Overview(summary, byTenant, byFeature, corrections);
    }

    public List<EvalRun> evalRuns(int limit) {
        context.requireAdmin();
        return jdbc.query("select * from eval_run order by created_at desc limit ?", (rs, i) -> new EvalRun(rs.getLong("id"),
                rs.getString("git_sha"), rs.getString("profile"), rs.getBoolean("passed"), json(rs.getString("metrics")),
                json(rs.getString("thresholds")), rs.getTimestamp("created_at").toInstant()), Math.min(Math.max(limit, 1), 200));
    }

    @Transactional
    public EvalRun recordEvalRun(EvalRunRequest request) {
        context.requireAdmin();
        if (request == null || request.metrics() == null || request.metrics().isEmpty()) {
            throw new BadRequestException("metrics are required");
        }
        Long id = jdbc.queryForObject("insert into eval_run (git_sha, profile, passed, metrics, thresholds) "
                        + "values (?, ?, ?, cast(? as jsonb), cast(? as jsonb)) returning id", Long.class,
                request.gitSha() == null || request.gitSha().isBlank() ? "local" : request.gitSha(),
                request.profile() == null ? ai.profileName() : request.profile(),
                Boolean.TRUE.equals(request.passed()), write(request.metrics()),
                write(request.thresholds() == null ? Map.of() : request.thresholds()));
        return evalRuns(200).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private static Long toLong(Object value) {
        return value instanceof Number n ? Math.round(n.doubleValue()) : null;
    }

    private Map<String, Object> json(String value) {
        try {
            return value == null ? Map.of() : mapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("invalid JSON");
        }
    }
}
