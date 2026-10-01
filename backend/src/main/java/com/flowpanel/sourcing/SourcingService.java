package com.flowpanel.sourcing;

import com.flowpanel.ai.AiGateway;
import com.flowpanel.ai.AiPrompt;
import com.flowpanel.ai.AiResult;
import com.flowpanel.ai.EmbeddingService;
import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.ConflictException;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.intake.IntakeService;
import com.flowpanel.intake.Order;
import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionEvents;
import com.flowpanel.mission.MissionPositions;
import com.flowpanel.mission.MissionService;
import com.flowpanel.mission.Phase;
import com.flowpanel.sourcing.CandidateRanking.Booking;
import com.flowpanel.sourcing.CandidateRanking.Evaluation;
import com.flowpanel.sourcing.CandidateRanking.OrderCriteria;
import com.flowpanel.sourcing.CandidateRanking.WorkerFacts;
import com.flowpanel.sourcing.PlacementRepository.Placement;
import com.flowpanel.tenant.Supplier;
import com.flowpanel.tenant.TenantService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SourcingService implements MissionPositions {

    public static final String SUMMARY_FEATURE = "sourcing.summary";
    static final int PROPOSALS_PER_SUPPLIER = 4;
    static final int SUMMARIES = 3;

    static final String SUMMARY_SYSTEM = """
            You help a staffing buyer read a candidate shortlist. Write ONE short sentence in English summarizing why the
            candidate fits or does not fit, using ONLY the facts given. Do not add facts, do not rank, do not decide.""";

    public record CandidateView(Long candidateId, Long workerId, String workerName, String supplierName, String city,
                                Integer rank, double score, double similarity, Double distanceKm, int experienceYears,
                                List<String> certifications, List<String> skills, boolean eligible,
                                List<String> exclusionReasons, List<CandidateRanking.Item> explanation, String aiSummary,
                                boolean selected) {
    }

    public record SourcingView(Long missionId, boolean published, Instant publishedAt, List<String> suppliers,
                               int quantity, int filled, boolean readOnly, List<CandidateView> candidates,
                               List<CandidateView> excluded) {
    }

    private final MissionService missions;
    private final IntakeService intake;
    private final TenantService tenants;
    private final WorkerRepository workers;
    private final CandidateRepository candidates;
    private final PlacementRepository placements;
    private final EmbeddingService embeddings;
    private final AiGateway gateway;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final RequestContext context;
    private final JdbcTemplate jdbc;

    public SourcingService(MissionService missions, IntakeService intake, TenantService tenants, WorkerRepository workers,
                           CandidateRepository candidates, PlacementRepository placements, EmbeddingService embeddings,
                           AiGateway gateway, ArtifactService artifacts, AuditService audit, RequestContext context,
                           JdbcTemplate jdbc) {
        this.missions = missions;
        this.intake = intake;
        this.tenants = tenants;
        this.workers = workers;
        this.candidates = candidates;
        this.placements = placements;
        this.embeddings = embeddings;
        this.gateway = gateway;
        this.artifacts = artifacts;
        this.audit = audit;
        this.context = context;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public SourcingView view(Long missionId) {
        Mission m = missions.requireReached(missionId, Phase.SOURCING);
        return toView(m, candidates.findByMissionIdOrderByEligibleDescScoreDesc(m.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Positions positions(Mission mission) {
        Integer total = intake.order(mission.getId()).map(Order::quantity).orElse(null);
        return new Positions(placements.count(mission.getId()), total);
    }

    @Transactional(readOnly = true)
    public List<Placement> placementsOf(Long missionId) {
        return placements.forMission(missionId);
    }

    // ------------------------------------------------------------------ commands

    /** Sends the order to every panel supplier and collects their (simulated) proposals, ranked and explained. */
    public SourcingView publish(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.SOURCING);
        if (isPublished(m.getId())) {
            throw new ConflictException("The order was already published to the panel");
        }
        Order order = intake.order(m.getId()).orElseThrow(() -> new ConflictException("The order is not confirmed"));
        List<Supplier> panel = tenants.panel(m.getTenantId());
        panel.forEach(s -> jdbc.update("insert into mission_supplier (mission_id, supplier_id) values (?, ?)", m.getId(), s.getId()));

        OrderCriteria criteria = new OrderCriteria(order.site(), order.startDate(), order.endDate(),
                order.requiredCertifications(), order.position());
        float[] orderVector = embeddings.embed("sourcing.embedding", orderText(order));
        List<Worker> pool = workers.findBySupplierIdInOrderByIdAsc(panel.stream().map(Supplier::getId).toList());
        List<float[]> vectors = embeddings.embedAll("sourcing.embedding", pool.stream().map(Worker::matchingText).toList());
        Map<Long, Double> similarity = new LinkedHashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            similarity.put(pool.get(i).getId(), EmbeddingService.cosine(orderVector, vectors.get(i)));
        }
        Map<Long, List<Booking>> bookings = placements.forWorkers(pool.stream().map(Worker::getId).toList()).stream()
                .filter(p -> !p.missionId().equals(m.getId()))
                .collect(Collectors.groupingBy(Placement::workerId,
                        Collectors.mapping(p -> new Booking(p.missionRef(), p.start(), p.end()), Collectors.toList())));

        // Each supplier proposes its closest profiles (simulated supplier behaviour), then rules and scores apply.
        List<Candidate> created = new ArrayList<>();
        for (Supplier supplier : panel) {
            pool.stream()
                    .filter(w -> w.getSupplierId().equals(supplier.getId()))
                    .sorted(Comparator.comparingDouble((Worker w) -> -similarity.get(w.getId())).thenComparing(Worker::getId))
                    .limit(PROPOSALS_PER_SUPPLIER)
                    .forEach(w -> {
                        Evaluation e = CandidateRanking.evaluate(criteria, facts(w), similarity.get(w.getId()),
                                bookings.getOrDefault(w.getId(), List.of()));
                        created.add(candidates.save(new Candidate(m.getId(), w.getId(), w.getSupplierId(), e)));
                    });
        }
        List<Candidate> eligible = created.stream().filter(Candidate::isEligible)
                .sorted(Comparator.comparing(Candidate::getScore).reversed().thenComparing(Candidate::getWorkerId)).toList();
        for (int i = 0; i < eligible.size(); i++) {
            eligible.get(i).setRank(i + 1);
        }
        audit.system(m.getId(), "sourcing.published", "Order sent to " + panel.size() + " panel suppliers; "
                + created.size() + " proposals, " + eligible.size() + " eligible after rules",
                Map.of("suppliers", panel.stream().map(Supplier::getName).toList(), "proposals", created.size(),
                        "eligible", eligible.size()));
        eligible.stream().limit(SUMMARIES).forEach(c -> summarize(m, c));
        audit.human(m.getId(), "sourcing.publish", "Published the order to the panel", Map.of());
        m.touch();
        syncShortlist(m, "DRAFT");
        return toView(m, candidates.findByMissionIdOrderByEligibleDescScoreDesc(m.getId()));
    }

    public SourcingView select(Long missionId, Long candidateId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.SOURCING);
        Candidate c = candidates.findByIdAndMissionId(candidateId, m.getId())
                .orElseThrow(() -> new NotFoundException("Candidate", candidateId));
        if (!c.isEligible()) {
            throw new ConflictException("Candidate is excluded: " + String.join("; ", c.getExclusionReasons()));
        }
        Order order = intake.order(m.getId()).orElseThrow();
        List<Placement> current = placements.forMission(m.getId());
        if (current.stream().anyMatch(p -> p.candidateId().equals(c.getId()))) {
            return toView(m, candidates.findByMissionIdOrderByEligibleDescScoreDesc(m.getId()));
        }
        if (current.size() >= order.quantity()) {
            throw new ConflictException("All " + order.quantity() + " positions are already filled");
        }
        // Fast path for a clear message; the exclusion constraint is the real guarantee (concurrent selections).
        placements.forWorkers(List.of(c.getWorkerId())).stream()
                .filter(p -> CandidateRanking.overlaps(p.start(), p.end(), order.startDate(), order.endDate()))
                .findFirst()
                .ifPresent(p -> {
                    throw doubleBooked(p);
                });
        try {
            placements.insert(m.getId(), c.getWorkerId(), c.getSupplierId(), c.getId(), order.startDate(), order.endDate(),
                    context.current().userId());
        } catch (DataIntegrityViolationException e) {
            throw placements.overlapping(c.getWorkerId(), order.startDate(), order.endDate())
                    .map(SourcingService::doubleBooked)
                    .orElseGet(() -> new ConflictException("Worker is already placed on an overlapping mission"));
        }
        Worker w = workers.findById(c.getWorkerId()).orElseThrow();
        audit.human(m.getId(), "sourcing.selected", "Selected " + w.fullName() + " (" + tenants.supplierName(c.getSupplierId())
                + ")", Map.of("candidateId", c.getId(), "workerId", w.getId(), "rank", String.valueOf(c.getRank())));
        m.touch();
        syncShortlist(m, "DRAFT");
        return toView(m, candidates.findByMissionIdOrderByEligibleDescScoreDesc(m.getId()));
    }

    public SourcingView unselect(Long missionId, Long candidateId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.SOURCING);
        Candidate c = candidates.findByIdAndMissionId(candidateId, m.getId())
                .orElseThrow(() -> new NotFoundException("Candidate", candidateId));
        if (placements.delete(m.getId(), c.getId()) > 0) {
            Worker w = workers.findById(c.getWorkerId()).orElseThrow();
            audit.human(m.getId(), "sourcing.unselected", "Removed " + w.fullName() + " from the shortlist",
                    Map.of("candidateId", c.getId()));
        }
        m.touch();
        syncShortlist(m, "DRAFT");
        return toView(m, candidates.findByMissionIdOrderByEligibleDescScoreDesc(m.getId()));
    }

    /**
     * A supplier user proposes one of its own workers on an order published to its agency (mission in SOURCING).
     * The same hard rules and score apply as for every other proposal. Scope checks are done by the caller.
     */
    public Candidate proposeBySupplier(Mission m, Worker w) {
        if (candidates.findByMissionIdOrderByEligibleDescScoreDesc(m.getId()).stream()
                .anyMatch(c -> c.getWorkerId().equals(w.getId()))) {
            throw new ConflictException(w.fullName() + " is already proposed on this order");
        }
        Order order = intake.order(m.getId()).orElseThrow(() -> new ConflictException("The order is not confirmed"));
        OrderCriteria criteria = new OrderCriteria(order.site(), order.startDate(), order.endDate(),
                order.requiredCertifications(), order.position());
        double similarity = EmbeddingService.cosine(embeddings.embed("sourcing.embedding", orderText(order)),
                embeddings.embed("sourcing.embedding", w.matchingText()));
        List<Booking> bookings = placements.forWorkers(List.of(w.getId())).stream()
                .filter(p -> !p.missionId().equals(m.getId()))
                .map(p -> new Booking(p.missionRef(), p.start(), p.end()))
                .toList();
        Candidate c = candidates.save(new Candidate(m.getId(), w.getId(), w.getSupplierId(),
                CandidateRanking.evaluate(criteria, facts(w), similarity, bookings)));
        rerank(m.getId());
        if (c.isEligible() && c.getRank() != null && c.getRank() <= SUMMARIES) {
            summarize(m, c);
        }
        audit.record(com.flowpanel.audit.ActorKind.HUMAN, m.getTenantId(), m.getId(), "sourcing.proposed",
                tenants.supplierName(w.getSupplierId()) + " proposed " + w.fullName()
                        + (c.isEligible() ? " (rank " + c.getRank() + ")" : " — excluded: " + String.join("; ", c.getExclusionReasons())),
                Map.of("candidateId", c.getId(), "workerId", w.getId(), "eligible", c.isEligible()));
        m.touch();
        return c;
    }

    /** A supplier withdraws one of its proposals, as long as the buyer has not selected it. */
    public void withdrawBySupplier(Mission m, Candidate c) {
        if (placements.forMission(m.getId()).stream().anyMatch(p -> p.candidateId().equals(c.getId()))) {
            throw new ConflictException("This candidate is already selected by the client; ask the client to unselect first");
        }
        Worker w = workers.findById(c.getWorkerId()).orElseThrow();
        candidates.delete(c);
        candidates.flush();
        rerank(m.getId());
        audit.record(com.flowpanel.audit.ActorKind.HUMAN, m.getTenantId(), m.getId(), "sourcing.withdrawn",
                tenants.supplierName(c.getSupplierId()) + " withdrew " + w.fullName(), Map.of("workerId", w.getId()));
        m.touch();
    }

    private void rerank(Long missionId) {
        List<Candidate> all = candidates.findByMissionIdOrderByEligibleDescScoreDesc(missionId);
        List<Candidate> eligible = all.stream().filter(Candidate::isEligible)
                .sorted(Comparator.comparing(Candidate::getScore).reversed().thenComparing(Candidate::getWorkerId)).toList();
        for (int i = 0; i < eligible.size(); i++) {
            eligible.get(i).setRank(i + 1);
        }
        all.stream().filter(c -> !c.isEligible()).forEach(c -> c.setRank(null));
    }

    @EventListener
    public void onPhaseFinalized(MissionEvents.PhaseFinalized event) {
        if (event.phase() == Phase.SOURCING) {
            syncShortlist(event.mission(), "FINAL");
        }
    }

    // ------------------------------------------------------------------ internals

    public boolean isPublished(Long missionId) {
        Integer n = jdbc.queryForObject("select count(*) from mission_supplier where mission_id = ?", Integer.class, missionId);
        return n != null && n > 0;
    }

    private Instant publishedAt(Long missionId) {
        return jdbc.query("select min(published_at) from mission_supplier where mission_id = ?",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null, missionId);
    }

    private void summarize(Mission m, Candidate c) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("score", c.getScore());
        facts.put("positives", c.getExplanation().stream().filter(CandidateRanking.Item::ok).map(CandidateRanking.Item::text).toList());
        facts.put("negatives", c.getExplanation().stream().filter(i -> !i.ok()).map(CandidateRanking.Item::text).toList());
        String user = "Candidate facts:\n+ " + String.join("\n+ ", castList(facts.get("positives")))
                + (castList(facts.get("negatives")).isEmpty() ? "" : "\n- " + String.join("\n- ", castList(facts.get("negatives"))));
        AiResult<String> result = gateway.text(AiPrompt.of(SUMMARY_FEATURE, m.getId(), SUMMARY_SYSTEM, user)
                .withFacts(facts).withMaxTokens(60));
        c.setSummary(result.value().strip(), result.aiCallId());
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object o) {
        return (List<String>) o;
    }

    private static ConflictException doubleBooked(Placement p) {
        return new ConflictException("Worker is already placed on " + p.missionRef() + " (" + p.start() + " → " + p.end() + ")",
                Map.of("conflictingMission", p.missionRef()));
    }

    private static String orderText(Order order) {
        return order.position() + ". Certifications : " + String.join(", ", order.requiredCertifications()) + ". "
                + order.schedule() + ".";
    }

    private static WorkerFacts facts(Worker w) {
        return new WorkerFacts(w.getId(), w.getCertifications(), w.getSkills(), w.getExperienceYears(), w.getAvailableFrom(),
                w.getAvailableTo(), new Geo.Point(w.getLatitude().doubleValue(), w.getLongitude().doubleValue()), w.getCity());
    }

    private void syncShortlist(Mission m, String status) {
        if (!isPublished(m.getId())) {
            return;
        }
        Map<Long, Worker> byId = workers.findAllById(placements.forMission(m.getId()).stream().map(Placement::workerId).toList())
                .stream().collect(Collectors.toMap(Worker::getId, Function.identity()));
        List<Map<String, Object>> selected = placements.forMission(m.getId()).stream()
                .map(p -> Map.<String, Object>of("worker", byId.get(p.workerId()).fullName(),
                        "supplier", tenants.supplierName(p.supplierId()), "start", p.start().toString(), "end", p.end().toString()))
                .toList();
        artifacts.upsert(m.getId(), Phase.SOURCING, ArtifactService.SHORTLIST, "SL-" + m.getNumber(), status,
                Map.of("selected", selected));
    }

    SourcingView toView(Mission m, List<Candidate> all) {
        int quantity = intake.order(m.getId()).map(Order::quantity).orElse(0);
        List<Placement> current = placements.forMission(m.getId());
        Map<Long, Worker> byId = workers.findAllById(all.stream().map(Candidate::getWorkerId).toList()).stream()
                .collect(Collectors.toMap(Worker::getId, Function.identity()));
        List<CandidateView> eligible = new ArrayList<>();
        List<CandidateView> excluded = new ArrayList<>();
        for (Candidate c : all) {
            Worker w = byId.get(c.getWorkerId());
            boolean selected = current.stream().anyMatch(p -> p.candidateId().equals(c.getId()));
            CandidateView v = toCandidateView(c, w, selected);
            (c.isEligible() ? eligible : excluded).add(v);
        }
        eligible.sort(Comparator.comparing((CandidateView v) -> v.rank() == null ? Integer.MAX_VALUE : v.rank()));
        List<String> suppliers = jdbc.queryForList("select s.name from mission_supplier ms join supplier s on s.id = ms.supplier_id "
                + "where ms.mission_id = ? order by s.id", String.class, m.getId());
        return new SourcingView(m.getId(), !suppliers.isEmpty(), publishedAt(m.getId()), suppliers, quantity, current.size(),
                m.getPhase() != Phase.SOURCING, eligible, excluded);
    }

    CandidateView toCandidateView(Candidate c, Worker w, boolean selected) {
        return new CandidateView(c.getId(), w.getId(), w.fullName(), tenants.supplierName(c.getSupplierId()), w.getCity(),
                c.getRank(), c.getScore().doubleValue(), c.getSimilarity().doubleValue(),
                c.getDistanceKm() == null ? null : c.getDistanceKm().doubleValue(), w.getExperienceYears(), w.getCertifications(),
                w.getSkills(), c.isEligible(), c.getExclusionReasons(), c.getExplanation(), c.getSummary(), selected);
    }
}
