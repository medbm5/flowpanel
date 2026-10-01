package com.flowpanel.sourcing;

import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.intake.IntakeService;
import com.flowpanel.intake.Order;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionRepository;
import com.flowpanel.mission.Phase;
import com.flowpanel.sourcing.PlacementRepository.Placement;
import com.flowpanel.sourcing.SourcingService.CandidateView;
import com.flowpanel.tenant.TenantService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a supplier user may see: orders published to their supplier, and only their own proposals and placements.
 * Scoping happens in the queries (supplier id from the session), never in the UI.
 */
@Service
@Transactional(readOnly = true)
public class SupplierPortalService {

    public record OrderSummary(Long missionId, String ref, String client, String title, String site, Phase phase,
                               String position, Integer quantity, LocalDate startDate, LocalDate endDate,
                               int myProposals, int myPlacements) {
    }

    public record PlacementView(Long workerId, String workerName, LocalDate start, LocalDate end) {
    }

    public record OrderDetail(OrderSummary order, List<CandidateView> myProposals, List<PlacementView> myPlacements) {
    }

    private final RequestContext context;
    private final JdbcTemplate jdbc;
    private final MissionRepository missions;
    private final IntakeService intake;
    private final TenantService tenants;
    private final CandidateRepository candidates;
    private final PlacementRepository placements;
    private final WorkerRepository workers;
    private final SourcingService sourcing;

    public SupplierPortalService(RequestContext context, JdbcTemplate jdbc, MissionRepository missions, IntakeService intake,
                                 TenantService tenants, CandidateRepository candidates, PlacementRepository placements,
                                 WorkerRepository workers, SourcingService sourcing) {
        this.context = context;
        this.jdbc = jdbc;
        this.missions = missions;
        this.intake = intake;
        this.tenants = tenants;
        this.candidates = candidates;
        this.placements = placements;
        this.workers = workers;
        this.sourcing = sourcing;
    }

    public List<OrderSummary> orders() {
        Long supplierId = context.requireSupplierId();
        List<Long> ids = jdbc.queryForList("select mission_id from mission_supplier where supplier_id = ? order by published_at desc",
                Long.class, supplierId);
        return missions.findAllById(ids).stream().map(m -> summary(m, supplierId)).toList();
    }

    public OrderDetail order(Long missionId) {
        Long supplierId = context.requireSupplierId();
        Mission m = publishedTo(missionId, supplierId);
        List<Candidate> mine = candidates.findByMissionIdAndSupplierIdOrderByEligibleDescScoreDesc(m.getId(), supplierId);
        List<Placement> myPlacements = placements.forMission(m.getId()).stream()
                .filter(p -> p.supplierId().equals(supplierId)).toList();
        Map<Long, Worker> byId = workers.findAllById(mine.stream().map(Candidate::getWorkerId).toList()).stream()
                .collect(Collectors.toMap(Worker::getId, Function.identity()));
        List<CandidateView> proposals = mine.stream()
                .map(c -> sourcing.toCandidateView(c, byId.get(c.getWorkerId()),
                        myPlacements.stream().anyMatch(p -> p.candidateId().equals(c.getId()))))
                .toList();
        List<PlacementView> placementViews = myPlacements.stream()
                .map(p -> new PlacementView(p.workerId(), workers.findById(p.workerId()).map(Worker::fullName).orElse(""),
                        p.start(), p.end()))
                .toList();
        return new OrderDetail(summary(m, supplierId), proposals, placementViews);
    }

    /** The mission, if its order was published to this supplier; otherwise 404. */
    public Mission publishedTo(Long missionId, Long supplierId) {
        Integer n = jdbc.queryForObject("select count(*) from mission_supplier where mission_id = ? and supplier_id = ?",
                Integer.class, missionId, supplierId);
        if (n == null || n == 0) {
            throw new NotFoundException("Order", missionId);
        }
        return missions.findById(missionId).orElseThrow(() -> new NotFoundException("Order", missionId));
    }

    private OrderSummary summary(Mission m, Long supplierId) {
        Order o = intake.order(m.getId()).orElse(null);
        int proposals = candidates.findByMissionIdAndSupplierIdOrderByEligibleDescScoreDesc(m.getId(), supplierId).size();
        int placed = (int) placements.forMission(m.getId()).stream().filter(p -> p.supplierId().equals(supplierId)).count();
        return new OrderSummary(m.getId(), m.getRef(), tenants.tenantName(m.getTenantId()), m.getTitle(), m.getSite(), m.getPhase(),
                o == null ? null : o.position(), o == null ? null : o.quantity(), o == null ? null : o.startDate(),
                o == null ? null : o.endDate(), proposals, placed);
    }
}
