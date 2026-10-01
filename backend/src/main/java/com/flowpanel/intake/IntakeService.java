package com.flowpanel.intake;

import com.flowpanel.ai.AiGateway;
import com.flowpanel.ai.AiPrompt;
import com.flowpanel.ai.AiProperties;
import com.flowpanel.ai.AiResult;
import com.flowpanel.audit.AuditService;
import com.flowpanel.common.BadRequestException;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionEvents;
import com.flowpanel.mission.MissionService;
import com.flowpanel.mission.Phase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IntakeService {

    public static final String FEATURE = "intake.extract";

    static final String SYSTEM_PROMPT = """
            You extract a temporary staffing order from an email written in French by a client site manager.
            Return JSON matching the schema. For every field give the value and your confidence between 0 and 1.
            Rules:
            - Dates in ISO format yyyy-MM-dd. Numbers with a dot as decimal separator, no currency symbol.
            - position: the job title in the singular (e.g. "Cariste", "Préparateur de commandes").
            - legalReason: ACTIVITY_INCREASE (accroissement temporaire d'activité), REPLACEMENT (remplacement d'un salarié
              absent) or SEASONAL (emploi saisonnier).
            - replacedEmployee: only for REPLACEMENT, exactly as written in the email (it may be a token like [PERSON_1]).
            - requiredCertifications: only mandatory ones, comma separated; empty if none or only "appreciated".
            - overtimeAllowed: "true" only if overtime is explicitly agreed, otherwise "false".
            - If a value is hedged ("normalement", "environ", "dès que possible", "à confirmer") or missing, lower the
              confidence below 0.7. Use an empty string when the email does not say. Never invent values.""";

    public record FieldView(String name, String label, String value, String aiValue, double confidence,
                            List<String> errors, boolean needsReview, boolean confirmed, boolean corrected) {
    }

    public record IntakeView(Long missionId, String emailText, boolean extracted, Instant extractedAt, boolean readOnly,
                             int needsReviewCount, double confidenceThreshold, List<FieldView> fields) {
    }

    public record PreviewView(List<FieldView> fields, String model, long latencyMs) {
    }

    public record ConfirmRequest(String value) {
    }

    private final MissionService missions;
    private final IntakeDraftRepository drafts;
    private final AiGateway gateway;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final AiProperties aiProperties;

    public IntakeService(MissionService missions, IntakeDraftRepository drafts, AiGateway gateway,
                         ArtifactService artifacts, AuditService audit, AiProperties aiProperties) {
        this.missions = missions;
        this.drafts = drafts;
        this.gateway = gateway;
        this.artifacts = artifacts;
        this.audit = audit;
        this.aiProperties = aiProperties;
    }

    @Transactional(readOnly = true)
    public IntakeView view(Long missionId) {
        Mission m = missions.getForTenant(missionId);
        return toView(m, drafts.findById(missionId).orElse(null));
    }

    public IntakeView extract(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.INTAKE);
        AiResult<OrderExtraction> result = gateway.structured(prompt(m.getId(), m.getSourceEmail()), OrderExtraction.class);
        List<DraftField> fields = buildFields(result.value());
        IntakeDraft draft = drafts.findById(m.getId())
                .map(d -> {
                    d.replace(result.aiCallId(), fields);
                    return d;
                })
                .orElseGet(() -> drafts.save(new IntakeDraft(m.getId(), result.aiCallId(), fields)));
        long flagged = fields.stream().filter(DraftField::needsReview).count();
        audit.ai(m.getId(), "intake.extracted", "AI extracted the order: " + fields.size() + " fields, " + flagged
                + " flagged for review", Map.of("aiCallId", result.aiCallId(), "flagged", flagged));
        Map<String, String> values = values(fields);
        if (m.getTemplateCode() == null && !values.getOrDefault("position", "").isBlank()) {
            m.rename(values.get("position") + (values.getOrDefault("site", "").isBlank() ? "" : " — " + values.get("site")),
                    values.get("site"));
        } else if (m.getSite() == null) {
            m.rename(null, values.get("site"));
        }
        m.touch();
        syncArtifact(m, draft, "DRAFT");
        return toView(m, draft);
    }

    public IntakeView confirm(Long missionId, String field, ConfirmRequest request) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.INTAKE);
        IntakeDraft draft = drafts.findById(missionId)
                .orElseThrow(() -> new BadRequestException("Extract the order before confirming fields"));
        if (!OrderValidator.FIELDS.contains(field)) {
            throw new NotFoundException("Field", field);
        }
        Map<String, String> values = values(draft.getFields());
        String newValue = request == null || request.value() == null ? values.get(field)
                : OrderValidator.normalize(field, request.value());
        values.put(field, newValue);
        Map<String, List<String>> errors = OrderValidator.validate(values);
        if (!errors.get(field).isEmpty()) {
            throw new BadRequestException(OrderValidator.LABELS.get(field) + " " + String.join("; ", errors.get(field)),
                    Map.of("field", field, "errors", errors.get(field)));
        }
        List<DraftField> updated = new ArrayList<>();
        for (DraftField f : draft.getFields()) {
            List<String> fieldErrors = errors.get(f.name());
            if (f.name().equals(field)) {
                boolean corrected = f.corrected() || !newValue.equals(f.aiValue());
                updated.add(new DraftField(f.name(), f.label(), newValue, f.aiValue(), f.confidence(), List.of(), false,
                        true, corrected));
            } else {
                boolean review = !fieldErrors.isEmpty() || (!f.confirmed() && f.confidence() < aiProperties.confidenceThreshold());
                updated.add(new DraftField(f.name(), f.label(), f.value(), f.aiValue(), f.confidence(), fieldErrors, review,
                        f.confirmed() && fieldErrors.isEmpty(), f.corrected()));
            }
        }
        draft.setFields(updated);
        DraftField confirmed = updated.stream().filter(f -> f.name().equals(field)).findFirst().orElseThrow();
        audit.human(m.getId(), confirmed.corrected() ? "intake.field.corrected" : "intake.field.confirmed",
                (confirmed.corrected() ? "Corrected " : "Confirmed ") + confirmed.label() + ": " + newValue,
                Map.of("field", field, "aiValue", String.valueOf(confirmed.aiValue()), "value", newValue));
        m.touch();
        syncArtifact(m, draft, "DRAFT");
        return toView(m, draft);
    }

    /** Extraction without a mission (used by the eval suite); nothing is persisted except the ai_call record. */
    public PreviewView preview(String emailText) {
        if (emailText == null || emailText.isBlank()) {
            throw new BadRequestException("emailText is required");
        }
        AiResult<OrderExtraction> result = gateway.structured(prompt(null, emailText), OrderExtraction.class);
        return new PreviewView(buildFields(result.value()).stream().map(IntakeService::toFieldView).toList(),
                result.model(), result.latencyMs());
    }

    /** The confirmed (or fully valid) order of a mission, for later phases. */
    @Transactional(readOnly = true)
    public Optional<Order> order(Long missionId) {
        return drafts.findById(missionId).flatMap(d -> {
            Map<String, String> values = values(d.getFields());
            boolean valid = OrderValidator.validate(values).values().stream().allMatch(List::isEmpty);
            return valid ? Optional.of(Order.from(values)) : Optional.empty();
        });
    }

    @Transactional(readOnly = true)
    public Optional<IntakeDraft> draft(Long missionId) {
        return drafts.findById(missionId);
    }

    @EventListener
    public void onPhaseFinalized(MissionEvents.PhaseFinalized event) {
        if (event.phase() == Phase.INTAKE) {
            drafts.findById(event.mission().getId()).ifPresent(d -> syncArtifact(event.mission(), d, "CONFIRMED"));
        }
    }

    // ------------------------------------------------------------------ internals

    private AiPrompt prompt(Long missionId, String email) {
        return AiPrompt.of(FEATURE, missionId, SYSTEM_PROMPT, email).withMaxTokens(700);
    }

    private List<DraftField> buildFields(OrderExtraction extraction) {
        Map<String, OrderExtraction.ExtractedField> raw = extraction.asMap();
        Map<String, String> values = new LinkedHashMap<>();
        raw.forEach((k, v) -> values.put(k, v.value() == null ? "" : v.value().strip()));
        Map<String, List<String>> errors = OrderValidator.validate(values);
        List<DraftField> fields = new ArrayList<>();
        for (String name : OrderValidator.FIELDS) {
            OrderExtraction.ExtractedField f = raw.get(name);
            double confidence = Math.max(0, Math.min(1, f.confidence()));
            List<String> fieldErrors = errors.get(name);
            boolean review = !fieldErrors.isEmpty() || confidence < aiProperties.confidenceThreshold();
            fields.add(new DraftField(name, OrderValidator.LABELS.get(name), values.get(name), values.get(name), confidence,
                    fieldErrors, review, false, false));
        }
        return fields;
    }

    private void syncArtifact(Mission m, IntakeDraft draft, String status) {
        Map<String, Object> payload = new LinkedHashMap<>(values(draft.getFields()));
        artifacts.upsert(m.getId(), Phase.INTAKE, ArtifactService.ORDER, m.getRef(), status, payload);
    }

    static Map<String, String> values(List<DraftField> fields) {
        Map<String, String> v = new LinkedHashMap<>();
        fields.forEach(f -> v.put(f.name(), f.value() == null ? "" : f.value()));
        return v;
    }

    private IntakeView toView(Mission m, IntakeDraft draft) {
        boolean readOnly = m.getPhase() != Phase.INTAKE;
        if (draft == null) {
            return new IntakeView(m.getId(), m.getSourceEmail(), false, null, readOnly, 0,
                    aiProperties.confidenceThreshold(), List.of());
        }
        List<FieldView> fields = draft.getFields().stream().map(IntakeService::toFieldView).toList();
        int review = (int) fields.stream().filter(FieldView::needsReview).count();
        return new IntakeView(m.getId(), m.getSourceEmail(), true, draft.getExtractedAt(), readOnly, review,
                aiProperties.confidenceThreshold(), fields);
    }

    private static FieldView toFieldView(DraftField f) {
        return new FieldView(f.name(), f.label(), f.value(), f.aiValue(), f.confidence(), f.errors(), f.needsReview(),
                f.confirmed(), f.corrected());
    }
}
