package com.flowpanel.sourcing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long missionId;
    private Long workerId;
    private Long supplierId;
    @Column(name = "rank")
    private Integer rank;
    private BigDecimal score;
    private BigDecimal similarity;
    private BigDecimal distanceKm;
    private boolean eligible;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> exclusionReasons;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<CandidateRanking.Item> explanation;
    private String summary;
    private Long summaryAiCallId;
    private Instant createdAt;

    protected Candidate() {
    }

    public Candidate(Long missionId, Long workerId, Long supplierId, CandidateRanking.Evaluation e) {
        this.missionId = missionId;
        this.workerId = workerId;
        this.supplierId = supplierId;
        this.score = BigDecimal.valueOf(e.score());
        this.similarity = BigDecimal.valueOf(e.similarity()).setScale(4, java.math.RoundingMode.HALF_UP);
        this.distanceKm = e.distanceKm() == null ? null : BigDecimal.valueOf(e.distanceKm());
        this.eligible = e.eligible();
        this.exclusionReasons = e.exclusionReasons();
        this.explanation = e.explanation();
        this.createdAt = Instant.now();
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public void setSummary(String summary, Long aiCallId) {
        this.summary = summary;
        this.summaryAiCallId = aiCallId;
    }

    public Long getId() {
        return id;
    }

    public Long getMissionId() {
        return missionId;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Integer getRank() {
        return rank;
    }

    public BigDecimal getScore() {
        return score;
    }

    public BigDecimal getSimilarity() {
        return similarity;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public boolean isEligible() {
        return eligible;
    }

    public List<String> getExclusionReasons() {
        return exclusionReasons;
    }

    public List<CandidateRanking.Item> getExplanation() {
        return explanation;
    }

    public String getSummary() {
        return summary;
    }

    public Long getSummaryAiCallId() {
        return summaryAiCallId;
    }
}
