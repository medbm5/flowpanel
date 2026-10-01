package com.flowpanel.metrics;

import com.flowpanel.ai.AiProperties;
import com.flowpanel.ai.SpendGuard;
import com.flowpanel.auth.RequestContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user LLM usage for the admin persona: calls, tokens, estimated OpenAI cost, latency, failures and refusals,
 * computed from {@code ai_call}. Calls without a recorded user are grouped as "Unattributed".
 */
@Service
@Transactional(readOnly = true)
public class UsageService {

    public static final List<Integer> WINDOWS = List.of(1, 7, 30, 90);

    public record ModelPrice(String model, BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
    }

    public record UsageConfig(String profile, String chatModel, String embeddingModel, BigDecimal dailyBudgetUsd,
                         BigDecimal liveSpendTodayUsd, double budgetUsedToday, int rateLimitPerMinute, int defaultMaxTokens,
                         List<ModelPrice> pricing) {
    }

    /** {@code userId} is null for the "Unattributed" row (calls made without a signed-in user). */
    public record UserUsage(Long userId, String displayName, String persona, String role, String organization, long calls,
                            long okCalls, long failedCalls, long refusedCalls, long retries, long inputTokens,
                            long outputTokens, BigDecimal costUsd, BigDecimal liveCostUsd, Long avgLatencyMs,
                            Long p95LatencyMs, Instant lastCallAt, double shareOfCost) {
    }

    public record UsageTotals(long calls, long okCalls, long failedCalls, long refusedCalls, long inputTokens, long outputTokens,
                         BigDecimal costUsd, BigDecimal liveCostUsd, BigDecimal avgDailyCostUsd,
                         BigDecimal projectedMonthlyCostUsd, long activeUsers) {
    }

    public record UsageOverview(int days, UsageConfig config, UsageTotals totals, List<UserUsage> users) {
    }

    public record UsageBreakdown(String key, long calls, long failedCalls, long inputTokens, long outputTokens, BigDecimal costUsd,
                            Long avgLatencyMs, Long p95LatencyMs) {
    }

    public record UsageDay(LocalDate day, long calls, long inputTokens, long outputTokens, BigDecimal costUsd) {
    }

    public record UsageCall(Long id, Instant createdAt, String feature, String kind, String model, String profile, String status,
                          int attempt, int inputTokens, int outputTokens, long latencyMs, BigDecimal costUsd,
                          String missionRef, String error) {
    }

    public record UsageUserDetail(int days, UserUsage user, double avgTokensPerCall, BigDecimal avgCostPerCallUsd,
                             double outputInputRatio, List<UsageBreakdown> byFeature, List<UsageBreakdown> byModel,
                             List<UsageBreakdown> byStatus, List<UsageDay> daily, List<UsageCall> recentCalls) {
    }

    private static final String AGGREGATES = """
            count(c.id) as calls,
            count(c.id) filter (where c.status = 'OK') as ok_calls,
            count(c.id) filter (where c.status in ('ERROR', 'INVALID_OUTPUT')) as failed_calls,
            count(c.id) filter (where c.status in ('BUDGET_EXCEEDED', 'RATE_LIMITED')) as refused_calls,
            count(c.id) filter (where c.attempt > 1) as retries,
            coalesce(sum(c.input_tokens), 0) as input_tokens,
            coalesce(sum(c.output_tokens), 0) as output_tokens,
            coalesce(sum(c.estimated_cost_usd), 0) as cost,
            coalesce(sum(c.estimated_cost_usd) filter (where c.profile = 'live'), 0) as live_cost,
            avg(c.latency_ms) filter (where c.status = 'OK') as avg_latency,
            percentile_cont(0.95) within group (order by c.latency_ms) filter (where c.status = 'OK') as p95_latency,
            max(c.created_at) as last_call
            """;

    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final AiProperties ai;
    private final SpendGuard spendGuard;

    public UsageService(JdbcTemplate jdbc, RequestContext context, AiProperties ai, SpendGuard spendGuard) {
        this.jdbc = jdbc;
        this.context = context;
        this.ai = ai;
        this.spendGuard = spendGuard;
    }

    public UsageOverview overview(int requestedDays) {
        context.requireAdmin();
        int days = window(requestedDays);
        List<UserUsage> users = new ArrayList<>(jdbc.query("""
                select u.id as user_id, u.display_name, u.persona, u.role,
                       coalesce(t.name, s.name, 'Flowpanel') as organization,
                """ + AGGREGATES + """
                from app_user u
                left join tenant t on t.id = u.tenant_id
                left join supplier s on s.id = u.supplier_id
                left join ai_call c on c.user_id = u.id and c.created_at >= now() - make_interval(days => ?)
                group by u.id, u.display_name, u.persona, u.role, t.name, s.name
                """, (rs, i) -> userRow(rs, rs.getLong("user_id"), rs.getString("display_name"), rs.getString("persona"),
                rs.getString("role"), rs.getString("organization")), days));
        UserUsage system = jdbc.queryForObject("select " + AGGREGATES
                        + " from ai_call c where c.user_id is null and c.created_at >= now() - make_interval(days => ?)",
                (rs, i) -> userRow(rs, null, "Unattributed", null, "SYSTEM",
                        "No recorded user (older calls or background jobs)"), days);
        if (system.calls() > 0) {
            users.add(system);
        }
        BigDecimal totalCost = users.stream().map(UserUsage::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        users = new ArrayList<>(users.stream().map(u -> withShare(u, totalCost)).toList());
        users.sort((a, b) -> b.costUsd().compareTo(a.costUsd()) != 0 ? b.costUsd().compareTo(a.costUsd())
                : Long.compare(b.calls(), a.calls()));

        BigDecimal liveCost = users.stream().map(UserUsage::liveCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgDaily = totalCost.divide(BigDecimal.valueOf(days), 6, RoundingMode.HALF_UP);
        UsageTotals totals = new UsageTotals(
                users.stream().mapToLong(UserUsage::calls).sum(),
                users.stream().mapToLong(UserUsage::okCalls).sum(),
                users.stream().mapToLong(UserUsage::failedCalls).sum(),
                users.stream().mapToLong(UserUsage::refusedCalls).sum(),
                users.stream().mapToLong(UserUsage::inputTokens).sum(),
                users.stream().mapToLong(UserUsage::outputTokens).sum(),
                totalCost, liveCost, avgDaily, avgDaily.multiply(BigDecimal.valueOf(30)).setScale(4, RoundingMode.HALF_UP),
                users.stream().filter(u -> u.userId() != null && u.calls() > 0).count());
        return new UsageOverview(days, config(), totals, users);
    }

    /** Detail for one user; {@code userId == null} means the "Unattributed" row. */
    public UsageUserDetail detail(Long userId, int requestedDays) {
        context.requireAdmin();
        int days = window(requestedDays);
        UsageOverview overview = overview(days);
        UserUsage user = overview.users().stream()
                .filter(u -> userId == null ? u.userId() == null : userId.equals(u.userId()))
                .findFirst()
                .orElseThrow(() -> new com.flowpanel.common.NotFoundException("User", userId == null ? "system" : userId));
        String scope = userId == null ? "c.user_id is null" : "c.user_id = ?";
        Object[] params = userId == null ? new Object[] {days} : new Object[] {userId, days};
        String where = " where " + scope + " and c.created_at >= now() - make_interval(days => ?)";

        List<UsageBreakdown> byFeature = breakdown("c.feature", where, params);
        List<UsageBreakdown> byModel = breakdown("c.model", where, params);
        List<UsageBreakdown> byStatus = breakdown("c.status", where, params);

        Object[] dailyParams = userId == null ? new Object[] {days - 1, days} : new Object[] {days - 1, userId, days};
        List<UsageDay> daily = jdbc.query("""
                with days as (
                    select generate_series(current_date - ?::int, current_date, interval '1 day')::date as day
                )
                select d.day, count(c.id) as calls, coalesce(sum(c.input_tokens), 0) as input_tokens,
                       coalesce(sum(c.output_tokens), 0) as output_tokens, coalesce(sum(c.estimated_cost_usd), 0) as cost
                from days d
                left join ai_call c on c.created_at::date = d.day and %s and c.created_at >= now() - make_interval(days => ?)
                group by d.day order by d.day
                """.formatted(scope), (rs, i) -> new UsageDay(rs.getObject("day", LocalDate.class), rs.getLong("calls"),
                rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getBigDecimal("cost")), dailyParams);

        List<UsageCall> recent = jdbc.query("""
                select c.id, c.created_at, c.feature, c.kind, c.model, c.profile, c.status, c.attempt, c.input_tokens,
                       c.output_tokens, c.latency_ms, c.estimated_cost_usd, m.ref as mission_ref, c.error
                from ai_call c left join mission m on m.id = c.mission_id
                """ + where + " order by c.created_at desc, c.id desc limit 30",
                (rs, i) -> new UsageCall(rs.getLong("id"), rs.getTimestamp("created_at").toInstant(), rs.getString("feature"),
                        rs.getString("kind"), rs.getString("model"), rs.getString("profile"), rs.getString("status"),
                        rs.getInt("attempt"), rs.getInt("input_tokens"), rs.getInt("output_tokens"), rs.getLong("latency_ms"),
                        rs.getBigDecimal("estimated_cost_usd"), rs.getString("mission_ref"), rs.getString("error")), params);

        long tokens = user.inputTokens() + user.outputTokens();
        double avgTokens = user.calls() == 0 ? 0 : (double) tokens / user.calls();
        BigDecimal avgCost = user.calls() == 0 ? BigDecimal.ZERO
                : user.costUsd().divide(BigDecimal.valueOf(user.calls()), 6, RoundingMode.HALF_UP);
        double ratio = user.inputTokens() == 0 ? 0 : (double) user.outputTokens() / user.inputTokens();
        return new UsageUserDetail(days, user, Math.round(avgTokens * 10) / 10.0, avgCost, Math.round(ratio * 1000) / 1000.0,
                byFeature, byModel, byStatus, daily, recent);
    }

    // ------------------------------------------------------------------ internals

    private List<UsageBreakdown> breakdown(String column, String where, Object[] params) {
        return jdbc.query("""
                select %s as key, count(c.id) as calls,
                       count(c.id) filter (where c.status in ('ERROR', 'INVALID_OUTPUT')) as failed_calls,
                       coalesce(sum(c.input_tokens), 0) as input_tokens, coalesce(sum(c.output_tokens), 0) as output_tokens,
                       coalesce(sum(c.estimated_cost_usd), 0) as cost,
                       avg(c.latency_ms) filter (where c.status = 'OK') as avg_latency,
                       percentile_cont(0.95) within group (order by c.latency_ms) filter (where c.status = 'OK') as p95_latency
                from ai_call c
                """.formatted(column) + where + " group by 1 order by cost desc, calls desc",
                (rs, i) -> new UsageBreakdown(rs.getString("key"), rs.getLong("calls"), rs.getLong("failed_calls"),
                        rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getBigDecimal("cost"),
                        toLong(rs.getObject("avg_latency")), toLong(rs.getObject("p95_latency"))), params);
    }

    private UsageConfig config() {
        BigDecimal spent = spendGuard.spentToday();
        double used = ai.dailyBudgetUsd().signum() == 0 ? 0
                : spent.divide(ai.dailyBudgetUsd(), 4, RoundingMode.HALF_UP).doubleValue();
        List<ModelPrice> pricing = ai.pricing() == null ? List.of() : ai.pricing().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ModelPrice(e.getKey(), e.getValue().inputPerMillion(), e.getValue().outputPerMillion()))
                .toList();
        return new UsageConfig(ai.profileName(), ai.chatModel(), ai.embeddingModel(), ai.dailyBudgetUsd(), spent, used,
                ai.rateLimitPerMinute(), ai.defaultMaxTokens(), pricing);
    }

    private static UserUsage userRow(ResultSet rs, Long userId, String name, String persona, String role, String organization)
            throws SQLException {
        return new UserUsage(userId, name, persona, role, organization, rs.getLong("calls"), rs.getLong("ok_calls"),
                rs.getLong("failed_calls"), rs.getLong("refused_calls"), rs.getLong("retries"), rs.getLong("input_tokens"),
                rs.getLong("output_tokens"), rs.getBigDecimal("cost"), rs.getBigDecimal("live_cost"),
                toLong(rs.getObject("avg_latency")), toLong(rs.getObject("p95_latency")),
                rs.getTimestamp("last_call") == null ? null : rs.getTimestamp("last_call").toInstant(), 0);
    }

    private static UserUsage withShare(UserUsage u, BigDecimal total) {
        double share = total.signum() == 0 ? 0 : u.costUsd().divide(total, 4, RoundingMode.HALF_UP).doubleValue();
        return new UserUsage(u.userId(), u.displayName(), u.persona(), u.role(), u.organization(), u.calls(), u.okCalls(),
                u.failedCalls(), u.refusedCalls(), u.retries(), u.inputTokens(), u.outputTokens(), u.costUsd(), u.liveCostUsd(),
                u.avgLatencyMs(), u.p95LatencyMs(), u.lastCallAt(), share);
    }

    private static int window(int days) {
        return WINDOWS.contains(days) ? days : 30;
    }

    private static Long toLong(Object value) {
        return value instanceof Number n ? Math.round(n.doubleValue()) : null;
    }
}
