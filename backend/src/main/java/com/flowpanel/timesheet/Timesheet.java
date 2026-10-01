package com.flowpanel.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "timesheet")
public class Timesheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long missionId;
    private Long contractId;
    private Long workerId;
    private Long supplierId;
    private LocalDate weekStart;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<BigDecimal> dailyHours;
    private BigDecimal contractedHours;
    private String status;
    private BigDecimal approvedHours;
    private Instant createdAt;
    private Instant updatedAt;

    protected Timesheet() {
    }

    public Timesheet(Long missionId, Long contractId, Long workerId, Long supplierId, LocalDate weekStart,
                     List<BigDecimal> dailyHours, BigDecimal contractedHours) {
        this.missionId = missionId;
        this.contractId = contractId;
        this.workerId = workerId;
        this.supplierId = supplierId;
        this.weekStart = weekStart;
        this.dailyHours = new ArrayList<>(dailyHours);
        this.contractedHours = contractedHours;
        this.status = "SUBMITTED";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public BigDecimal total() {
        return dailyHours.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The supplier sends a corrected sheet (simulated). */
    public void correct(List<BigDecimal> hours) {
        this.dailyHours = new ArrayList<>(hours);
        this.status = "CORRECTED";
        this.updatedAt = Instant.now();
    }

    public void approve(BigDecimal hours) {
        this.approvedHours = hours;
        this.status = "APPROVED";
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMissionId() {
        return missionId;
    }

    public Long getContractId() {
        return contractId;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public List<BigDecimal> getDailyHours() {
        return dailyHours;
    }

    public BigDecimal getContractedHours() {
        return contractedHours;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getApprovedHours() {
        return approvedHours;
    }
}
