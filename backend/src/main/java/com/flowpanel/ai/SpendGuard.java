package com.flowpanel.ai;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Spend protection for live calls: a daily USD budget (from ai_call records) and a per-tenant rate limit. */
@Component
public class SpendGuard {

    private final AiCallRepository calls;
    private final AiProperties props;
    private final Clock clock;
    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    @Autowired
    public SpendGuard(AiCallRepository calls, AiProperties props) {
        this(calls, props, Clock.systemUTC());
    }

    SpendGuard(AiCallRepository calls, AiProperties props, Clock clock) {
        this.calls = calls;
        this.props = props;
        this.clock = clock;
    }

    public BigDecimal spentToday() {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
        return calls.liveSpendSince(startOfDay);
    }

    public boolean budgetExhausted() {
        return spentToday().compareTo(props.dailyBudgetUsd()) >= 0;
    }

    /** Sliding one-minute window per tenant (or supplier / platform key). Returns false when over the limit. */
    public boolean tryAcquire(String scopeKey) {
        Instant now = clock.instant();
        Deque<Instant> window = windows.computeIfAbsent(scopeKey, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minusSeconds(60))) {
                window.pollFirst();
            }
            if (window.size() >= props.rateLimitPerMinute()) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
