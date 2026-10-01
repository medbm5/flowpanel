package com.flowpanel.ai;

import com.flowpanel.audit.ActorKind;
import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.CurrentUser;
import com.flowpanel.auth.RequestContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one {@code ai_call} row and one AI audit event per attempt, in its own transaction: spend and
 * failures are recorded even when the calling business transaction rolls back.
 */
@Component
public class AiCallRecorder {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiCallRepository repository;
    private final AuditService audit;
    private final RequestContext context;
    private final AiProperties props;

    public AiCallRecorder(AiCallRepository repository, AuditService audit, RequestContext context, AiProperties props) {
        this.repository = repository;
        this.audit = audit;
        this.context = context;
        this.props = props;
    }

    public record Attempt(String feature, Long missionId, AiCall.Kind kind, String model, int inputTokens,
                          int outputTokens, long latencyMs, AiCall.Status status, int attempt, String error,
                          String promptHash) {
    }

    public BigDecimal estimateCost(String model, int inputTokens, int outputTokens) {
        AiProperties.Price price = props.priceOf(model);
        return price.inputPerMillion().multiply(BigDecimal.valueOf(inputTokens))
                .add(price.outputPerMillion().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiCall record(Attempt a) {
        CurrentUser user = context.currentOptional().orElse(null);
        BigDecimal cost = a.status() == AiCall.Status.BUDGET_EXCEEDED || a.status() == AiCall.Status.RATE_LIMITED
                ? BigDecimal.ZERO : estimateCost(a.model(), a.inputTokens(), a.outputTokens());
        AiCall call = repository.save(new AiCall(user == null ? null : user.tenantId(),
                user == null ? null : user.supplierId(), user == null ? null : user.userId(), a.missionId(), a.feature(), a.kind(), props.profileName(),
                a.model(), a.inputTokens(), a.outputTokens(), a.latencyMs(), cost, a.status(), a.attempt(), a.error(),
                a.promptHash()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("aiCallId", call.getId());
        details.put("feature", a.feature());
        details.put("model", a.model());
        details.put("status", a.status().name());
        details.put("tokens", a.inputTokens() + a.outputTokens());
        if (a.error() != null) {
            details.put("error", call.getError());
        }
        String summary = a.status() == AiCall.Status.OK
                ? "AI " + a.feature() + " (" + a.model() + ", " + (a.inputTokens() + a.outputTokens()) + " tokens)"
                : "AI " + a.feature() + " failed: " + a.status();
        audit.record(ActorKind.AI, null, a.missionId(), "ai." + a.feature(), summary, details);
        return call;
    }
}
