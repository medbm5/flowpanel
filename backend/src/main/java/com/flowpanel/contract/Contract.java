package com.flowpanel.contract;

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
@Table(name = "contract")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long missionId;
    private Long placementId;
    private Long workerId;
    private Long supplierId;
    private String ref;
    private String position;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal hourlyRate;
    private BigDecimal weeklyHours;
    private boolean overtimeAllowed;
    private String legalReason;
    private String replacedEmployee;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> attachedCertificates;
    private String content;
    private Long contentAiCallId;
    private String status;
    private Instant signedByClientAt;
    private Instant signedBySupplierAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected Contract() {
    }

    public Contract(Long missionId, Long placementId, Long workerId, Long supplierId, String ref, ContractTerms terms) {
        this.missionId = missionId;
        this.placementId = placementId;
        this.workerId = workerId;
        this.supplierId = supplierId;
        this.ref = ref;
        apply(terms);
        this.status = "DRAFT";
        this.content = "";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public ContractTerms terms() {
        return new ContractTerms(position, startDate, endDate, hourlyRate, weeklyHours, overtimeAllowed, legalReason,
                replacedEmployee, attachedCertificates == null ? List.of() : List.copyOf(attachedCertificates));
    }

    public void apply(ContractTerms t) {
        this.position = t.position();
        this.startDate = t.startDate();
        this.endDate = t.endDate();
        this.hourlyRate = t.hourlyRate();
        this.weeklyHours = t.weeklyHours();
        this.overtimeAllowed = t.overtimeAllowed();
        this.legalReason = t.legalReason();
        this.replacedEmployee = t.replacedEmployee();
        this.attachedCertificates = new ArrayList<>(t.attachedCertificates());
        this.updatedAt = Instant.now();
    }

    public void setContent(String content, Long aiCallId) {
        this.content = content;
        this.contentAiCallId = aiCallId;
    }

    public void sign() {
        Instant now = Instant.now();
        this.signedByClientAt = now;
        this.signedBySupplierAt = now;
        this.status = "SIGNED";
        this.updatedAt = now;
    }

    public boolean isSigned() {
        return "SIGNED".equals(status);
    }

    public Long getId() {
        return id;
    }

    public Long getMissionId() {
        return missionId;
    }

    public Long getPlacementId() {
        return placementId;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getRef() {
        return ref;
    }

    public String getContent() {
        return content;
    }

    public Long getContentAiCallId() {
        return contentAiCallId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getSignedByClientAt() {
        return signedByClientAt;
    }

    public Instant getSignedBySupplierAt() {
        return signedBySupplierAt;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public BigDecimal getWeeklyHours() {
        return weeklyHours;
    }

    public boolean isOvertimeAllowed() {
        return overtimeAllowed;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
