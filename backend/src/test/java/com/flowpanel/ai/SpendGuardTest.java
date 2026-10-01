package com.flowpanel.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpendGuardTest {

    private final AiCallRepository repo = mock(AiCallRepository.class);
    private final AiProperties props = new AiProperties("live", "k", "gpt-4o-mini", "text-embedding-3-small",
            new BigDecimal("2.00"), 3, 800, 0.75, Map.of());
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void budgetIsExhaustedAtOrAboveTheDailyLimit() {
        SpendGuard guard = new SpendGuard(repo, props, clock);
        when(repo.liveSpendSince(any())).thenReturn(new BigDecimal("1.99"));
        assertThat(guard.budgetExhausted()).isFalse();
        when(repo.liveSpendSince(any())).thenReturn(new BigDecimal("2.00"));
        assertThat(guard.budgetExhausted()).isTrue();
    }

    @Test
    void budgetWindowStartsAtUtcMidnight() {
        SpendGuard guard = new SpendGuard(repo, props, clock);
        when(repo.liveSpendSince(Instant.parse("2026-10-01T00:00:00Z"))).thenReturn(new BigDecimal("0.5"));
        assertThat(guard.spentToday()).isEqualByComparingTo("0.5");
    }

    @Test
    void rateLimitIsPerScope() {
        SpendGuard guard = new SpendGuard(repo, props, clock);
        assertThat(guard.tryAcquire("tenant:1")).isTrue();
        assertThat(guard.tryAcquire("tenant:1")).isTrue();
        assertThat(guard.tryAcquire("tenant:1")).isTrue();
        assertThat(guard.tryAcquire("tenant:1")).isFalse();
        assertThat(guard.tryAcquire("tenant:2")).isTrue();
    }
}
