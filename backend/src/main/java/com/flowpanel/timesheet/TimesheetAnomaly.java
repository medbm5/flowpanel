package com.flowpanel.timesheet;

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
@Table(name = "timesheet_anomaly")
public class TimesheetAnomaly {

    public enum Resolution { APPROVE_OVERTIME, RETURN_TO_SUPPLIER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long missionId;
    private Long timesheetId;
    private String ruleId;
    private String message;
    private BigDecimal expectedHours;
    private BigDecimal actualHours;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Integer> flaggedDays;
    private String explanation;
    private Long explanationAiCallId;
    private String status;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Resolution resolution;
    private Long resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;

    protected TimesheetAnomaly() {
    }

    public TimesheetAnomaly(Long missionId, Long timesheetId, TimesheetRulesEngine.Anomaly a) {
        this.missionId = missionId;
        this.timesheetId = timesheetId;
        this.ruleId = a.ruleId();
        this.message = a.message();
        this.expectedHours = a.expected();
        this.actualHours = a.actual();
        this.flaggedDays = a.flaggedDays();
        this.status = "OPEN";
        this.createdAt = Instant.now();
    }

    public void explain(String text, Long aiCallId) {
        this.explanation = text;
        this.explanationAiCallId = aiCallId;
    }

    public void resolve(Resolution resolution, Long userId) {
        this.resolution = resolution;
        this.resolvedBy = userId;
        this.resolvedAt = Instant.now();
        this.status = "RESOLVED";
    }

    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public Long getId() {
        return id;
    }

    public Long getMissionId() {
        return missionId;
    }

    public Long getTimesheetId() {
        return timesheetId;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getExpectedHours() {
        return expectedHours;
    }

    public BigDecimal getActualHours() {
        return actualHours;
    }

    public List<Integer> getFlaggedDays() {
        return flaggedDays;
    }

    public String getExplanation() {
        return explanation;
    }

    public Long getExplanationAiCallId() {
        return explanationAiCallId;
    }

    public String getStatus() {
        return status;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
