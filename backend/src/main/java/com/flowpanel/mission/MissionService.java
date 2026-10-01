package com.flowpanel.mission;

import com.flowpanel.audit.AuditEvent;
import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.AppUser;
import com.flowpanel.auth.AppUserRepository;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.BadRequestException;
import com.flowpanel.common.ConflictException;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.mission.MissionDtos.ArtifactView;
import com.flowpanel.mission.MissionDtos.AuditView;
import com.flowpanel.mission.MissionDtos.CreateMissionRequest;
import com.flowpanel.mission.MissionDtos.GateView;
import com.flowpanel.mission.MissionDtos.MissionDetail;
import com.flowpanel.mission.MissionDtos.MissionSummary;
import com.flowpanel.mission.MissionDtos.PhaseState;
import com.flowpanel.mission.MissionDtos.PhaseStep;
import com.flowpanel.mission.MissionDtos.PositionsView;
import com.flowpanel.mission.MissionDtos.TemplateView;
import com.flowpanel.mission.gate.GateEvaluation;
import com.flowpanel.mission.gate.GateRegistry;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MissionService {

    private static final Pattern SUBJECT = Pattern.compile("(?im)^\\s*(?:objet|subject)\\s*:\\s*(.+)$");

    private final MissionRepository missions;
    private final PhaseCompletionRepository completions;
    private final RequestTemplateRepository templates;
    private final ArtifactService artifacts;
    private final GateRegistry gates;
    private final AuditService audit;
    private final RequestContext context;
    private final AppUserRepository users;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<MissionPositions> positionsProvider;

    public MissionService(MissionRepository missions, PhaseCompletionRepository completions,
                          RequestTemplateRepository templates, ArtifactService artifacts, GateRegistry gates,
                          AuditService audit, RequestContext context, AppUserRepository users,
                          ApplicationEventPublisher events, ObjectProvider<MissionPositions> positionsProvider) {
        this.missions = missions;
        this.completions = completions;
        this.templates = templates;
        this.artifacts = artifacts;
        this.gates = gates;
        this.audit = audit;
        this.context = context;
        this.users = users;
        this.events = events;
        this.positionsProvider = positionsProvider;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<MissionSummary> list(String status) {
        Long tenantId = context.requireTenantId();
        String filter = status == null ? "all" : status.toLowerCase();
        if (!List.of("all", "open", "review", "closed").contains(filter)) {
            throw new BadRequestException("status must be one of all, open, review, closed");
        }
        List<MissionSummary> result = new ArrayList<>();
        for (Mission m : missions.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            GateEvaluation gate = gates.evaluate(m);
            boolean closed = m.getPhase() == Phase.CLOSED;
            boolean keep = switch (filter) {
                case "open" -> !closed;
                case "review" -> !closed && gate.needsReview();
                case "closed" -> closed;
                default -> true;
            };
            if (keep) {
                result.add(new MissionSummary(m.getId(), m.getRef(), m.getTitle(), m.getSite(), m.getPhase(),
                        phaseSteps(m), positions(m), gate.nextAction(), gate.needsReview(), m.getCreatedAt(),
                        m.getUpdatedAt()));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public MissionDetail detail(Long id) {
        return toDetail(getForTenant(id));
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        return templates.findAllByOrderByCodeAsc().stream()
                .map(t -> new TemplateView(t.getCode(), t.getTitle(), t.getSite(), t.getDescription(), t.getEmailText()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditView> audit(Long id) {
        Mission m = getForTenant(id);
        return audit.forMission(m.getTenantId(), m.getId()).stream().map(MissionService::toAuditView).toList();
    }

    /** Mission of the caller's tenant, or 404. */
    @Transactional(readOnly = true)
    public Mission getForTenant(Long id) {
        Long tenantId = context.requireTenantId();
        return missions.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Mission", id));
    }

    /**
     * Guard for every phase-specific write: loads the caller's mission with a row lock and rejects the call (409)
     * when the mission is not in the expected phase.
     */
    public Mission requireCurrentPhase(Long id, Phase expected) {
        Long tenantId = context.requireTenantId();
        Mission m = missions.lockByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Mission", id));
        if (m.getPhase() != expected) {
            throw new ConflictException(expected.label() + " actions are not allowed: the mission is in phase "
                    + m.getPhase().label(), Map.of("currentPhase", m.getPhase().name(), "expectedPhase", expected.name()));
        }
        return m;
    }

    /** Read access for a phase screen: the phase must be reached (current or finalized). */
    @Transactional(readOnly = true)
    public Mission requireReached(Long id, Phase phase) {
        Mission m = getForTenant(id);
        if (m.getPhase().isBefore(phase)) {
            throw new ConflictException(phase.label() + " is locked until " + m.getPhase().label() + " is finalized",
                    Map.of("currentPhase", m.getPhase().name()));
        }
        return m;
    }

    // ---------------------------------------------------------------- commands

    public MissionDetail create(CreateMissionRequest request) {
        Long tenantId = context.requireTenantId();
        boolean hasTemplate = request.templateCode() != null && !request.templateCode().isBlank();
        boolean hasEmail = request.emailText() != null && !request.emailText().isBlank();
        if (hasTemplate == hasEmail) {
            throw new BadRequestException("Provide either templateCode or emailText");
        }
        String title;
        String site = null;
        String email;
        String templateCode = null;
        if (hasTemplate) {
            RequestTemplate t = templates.findById(request.templateCode())
                    .orElseThrow(() -> new NotFoundException("Request template", request.templateCode()));
            title = t.getTitle();
            site = t.getSite();
            email = t.getEmailText();
            templateCode = t.getCode();
        } else {
            email = request.emailText().strip();
            title = subjectOf(email);
        }
        if (request.title() != null && !request.title().isBlank()) {
            title = request.title().strip();
        }
        int number = (int) missions.nextNumber();
        Mission m = missions.save(new Mission(tenantId, number, ref(number), title, site, templateCode, email,
                context.current().userId()));
        audit.human(m.getId(), "mission.created", "Created mission " + m.getRef() + " — " + m.getTitle(),
                templateCode == null ? Map.of("source", "email") : Map.of("source", "template", "template", templateCode));
        return toDetail(m);
    }

    /** Creates a mission with a fixed number (demo seed only). */
    public Mission createSeeded(Long tenantId, int number, String title, String site, String templateCode,
                                String email, Long createdBy) {
        return missions.save(new Mission(tenantId, number, ref(number), title, site, templateCode, email, createdBy));
    }

    public MissionDetail finalizePhase(Long id, Phase phase) {
        Mission m = requireCurrentPhase(id, phase);
        Phase next = phase.next().orElseThrow(() -> new ConflictException("A closed mission cannot be finalized"));
        GateEvaluation gate = gates.evaluate(m);
        if (!gate.ready()) {
            throw new ConflictException(phase.label() + " cannot be finalized: " + gate.unmetLabels().size()
                    + " check(s) not met", Map.of("phase", phase.name(), "unmetChecks", gate.unmetLabels()));
        }
        completions.save(new PhaseCompletion(m.getId(), phase, context.current().userId()));
        events.publishEvent(new MissionEvents.PhaseFinalized(m, phase));
        m.transitionTo(next);
        audit.human(m.getId(), "phase.finalized", phase.label() + " finalized, mission moved to " + next.label(),
                Map.of("phase", phase.name(), "next", next.name()));
        events.publishEvent(new MissionEvents.PhaseEntered(m, next));
        return toDetail(m);
    }

    // ---------------------------------------------------------------- mapping

    public MissionDetail toDetail(Mission m) {
        GateEvaluation gate = gates.evaluate(m);
        List<ArtifactView> artifactViews = artifacts.forMission(m.getId()).stream()
                .map(a -> new ArtifactView(a.getId(), a.getPhase(), a.getType(), a.getRef(), a.getStatus(), a.getPayload(),
                        a.getUpdatedAt()))
                .toList();
        return new MissionDetail(m.getId(), m.getRef(), m.getTitle(), m.getSite(), m.getPhase(),
                m.getPhase() == Phase.CLOSED, gate.needsReview(), gate.nextAction(),
                new GateView(m.getPhase(), gate.checks(), gate.ready()), phaseSteps(m), artifactViews, positions(m),
                m.getSourceEmail(), m.getTemplateCode(), m.getCreatedAt(), m.getClosedAt(), m.getSummary());
    }

    public GateEvaluation evaluate(Mission m) {
        return gates.evaluate(m);
    }

    private List<PhaseStep> phaseSteps(Mission m) {
        Map<Phase, PhaseCompletion> done = completions.findByMissionIdOrderByFinalizedAtAsc(m.getId()).stream()
                .collect(Collectors.toMap(PhaseCompletion::getPhase, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, String> names = users.findAllById(done.values().stream().map(PhaseCompletion::getFinalizedBy)
                        .filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        List<PhaseStep> steps = new ArrayList<>();
        for (Phase p : Phase.values()) {
            PhaseCompletion c = done.get(p);
            PhaseState state;
            if (c != null || (p == Phase.CLOSED && m.getPhase() == Phase.CLOSED)) {
                state = PhaseState.DONE;
            } else if (p == m.getPhase()) {
                state = PhaseState.ACTIVE;
            } else {
                state = PhaseState.LOCKED;
            }
            steps.add(new PhaseStep(p, p.label(), state, c == null ? null : c.getFinalizedAt(),
                    c == null ? null : names.get(c.getFinalizedBy())));
        }
        return steps;
    }

    private PositionsView positions(Mission m) {
        MissionPositions provider = positionsProvider.getIfAvailable();
        if (provider == null) {
            return new PositionsView(0, null);
        }
        MissionPositions.Positions p = provider.positions(m);
        return new PositionsView(p.filled(), p.total());
    }

    static AuditView toAuditView(AuditEvent e) {
        return new AuditView(e.getId(), e.getActorKind().name(), e.getActorName(), e.getAction(), e.getSummary(),
                e.getDetails(), e.getCreatedAt());
    }

    public static String ref(int number) {
        return "ORD-" + Year.now().getValue() + "-" + String.format("%04d", number);
    }

    static String subjectOf(String email) {
        Matcher matcher = SUBJECT.matcher(email);
        if (matcher.find()) {
            String subject = matcher.group(1).strip();
            return subject.length() > 120 ? subject.substring(0, 120) : subject;
        }
        return "New staffing request";
    }
}
