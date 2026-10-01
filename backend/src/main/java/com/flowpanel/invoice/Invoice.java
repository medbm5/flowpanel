package com.flowpanel.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long missionId;
    private Long supplierId;
    private String ref;
    private byte[] pdf;
    private String extractedText;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private InvoiceLines extraction;
    private Long extractionAiCallId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ThreeWayMatcher.Result matchResult;
    private String status;
    private String messageDraft;
    private Long messageAiCallId;
    private String creditNoteRef;
    private BigDecimal creditNoteAmount;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> creditNote;
    private Instant approvedAt;
    private Instant createdAt;

    protected Invoice() {
    }

    public Invoice(Long missionId, Long supplierId, String ref, byte[] pdf, String extractedText) {
        this.missionId = missionId;
        this.supplierId = supplierId;
        this.ref = ref;
        this.pdf = pdf;
        this.extractedText = extractedText;
        this.status = "RECEIVED";
        this.createdAt = Instant.now();
    }

    public void extracted(InvoiceLines lines, Long aiCallId) {
        this.extraction = lines;
        this.extractionAiCallId = aiCallId;
    }

    public void matched(ThreeWayMatcher.Result result) {
        this.matchResult = result;
        this.status = result.matched() ? "MATCHED" : "MISMATCH";
    }

    public void drafted(String message, Long aiCallId) {
        this.messageDraft = message;
        this.messageAiCallId = aiCallId;
    }

    public void creditNote(String ref, BigDecimal amount, Map<String, Object> details) {
        this.creditNoteRef = ref;
        this.creditNoteAmount = amount;
        this.creditNote = details;
    }

    public void approve() {
        this.status = "APPROVED";
        this.approvedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMissionId() {
        return missionId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getRef() {
        return ref;
    }

    public byte[] getPdf() {
        return pdf;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public InvoiceLines getExtraction() {
        return extraction;
    }

    public Long getExtractionAiCallId() {
        return extractionAiCallId;
    }

    public ThreeWayMatcher.Result getMatchResult() {
        return matchResult;
    }

    public String getStatus() {
        return status;
    }

    public String getMessageDraft() {
        return messageDraft;
    }

    public String getCreditNoteRef() {
        return creditNoteRef;
    }

    public BigDecimal getCreditNoteAmount() {
        return creditNoteAmount;
    }

    public Map<String, Object> getCreditNote() {
        return creditNote;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
