package com.flowpanel.intake;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "intake_draft")
public class IntakeDraft {

    @Id
    private Long missionId;
    private Long aiCallId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<DraftField> fields;
    private Instant extractedAt;
    private Instant updatedAt;

    protected IntakeDraft() {
    }

    public IntakeDraft(Long missionId, Long aiCallId, List<DraftField> fields) {
        this.missionId = missionId;
        replace(aiCallId, fields);
    }

    public void replace(Long aiCallId, List<DraftField> fields) {
        this.aiCallId = aiCallId;
        this.fields = new ArrayList<>(fields);
        this.extractedAt = Instant.now();
        this.updatedAt = extractedAt;
    }

    public void setFields(List<DraftField> fields) {
        this.fields = new ArrayList<>(fields);
        this.updatedAt = Instant.now();
    }

    public Long getMissionId() {
        return missionId;
    }

    public Long getAiCallId() {
        return aiCallId;
    }

    public List<DraftField> getFields() {
        return fields;
    }

    public Instant getExtractedAt() {
        return extractedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
