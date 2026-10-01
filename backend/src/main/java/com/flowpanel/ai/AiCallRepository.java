package com.flowpanel.ai;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiCallRepository extends JpaRepository<AiCall, Long> {

    @Query("select coalesce(sum(c.estimatedCostUsd), 0) from AiCall c where c.profile = 'live' and c.createdAt >= :since")
    BigDecimal liveSpendSince(Instant since);
}
