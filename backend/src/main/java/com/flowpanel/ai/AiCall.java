package com.flowpanel.ai;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_call")
public class AiCall {

    public enum Status { OK, INVALID_OUTPUT, ERROR, BUDGET_EXCEEDED, RATE_LIMITED }

    public enum Kind { STRUCTURED, TEXT, TOOLS, EMBEDDING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long tenantId;
    private Long supplierId;
    private Long userId;
    private Long missionId;
    private String feature;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Kind kind;
    private String profile;
    private String model;
    private int inputTokens;
    private int outputTokens;
    private long latencyMs;
    private BigDecimal estimatedCostUsd;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;
    private int attempt;
    private String error;
    private String promptHash;
    private Instant createdAt;

    protected AiCall() {
    }

    public AiCall(Long tenantId, Long supplierId, Long userId, Long missionId, String feature, Kind kind, String profile, String model,
                  int inputTokens, int outputTokens, long latencyMs, BigDecimal estimatedCostUsd, Status status,
                  int attempt, String error, String promptHash) {
        this.tenantId = tenantId;
        this.supplierId = supplierId;
        this.userId = userId;
        this.missionId = missionId;
        this.feature = feature;
        this.kind = kind;
        this.profile = profile;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.estimatedCostUsd = estimatedCostUsd;
        this.status = status;
        this.attempt = attempt;
        this.error = error == null ? null : error.length() > 1000 ? error.substring(0, 1000) : error;
        this.promptHash = promptHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getMissionId() {
        return missionId;
    }

    public String getFeature() {
        return feature;
    }

    public Kind getKind() {
        return kind;
    }

    public String getProfile() {
        return profile;
    }

    public String getModel() {
        return model;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getError() {
        return error;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
