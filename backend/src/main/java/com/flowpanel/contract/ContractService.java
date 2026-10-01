package com.flowpanel.contract;

import com.flowpanel.ai.AiGateway;
import com.flowpanel.ai.AiPrompt;
import com.flowpanel.ai.AiResult;
import com.flowpanel.audit.AuditService;
import com.flowpanel.common.ConflictException;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.contract.ContractRulesEngine.RuleResult;
import com.flowpanel.intake.IntakeService;
import com.flowpanel.intake.Order;
import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionService;
import com.flowpanel.mission.Phase;
import com.flowpanel.sourcing.PlacementRepository.Placement;
import com.flowpanel.sourcing.SourcingService;
import com.flowpanel.sourcing.Worker;
import com.flowpanel.sourcing.WorkerRepository;
import com.flowpanel.tenant.TenantService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContractService {

    public static final String FEATURE = "contract.draft";

    static final String SYSTEM_PROMPT = """
            You draft the body of a French temporary work assignment contract ("contrat de mise à disposition") from the
            structured terms given. Use only the given facts; do not change any value, date, rate or name. Plain text,
            short numbered clauses, in French. The legal terms are checked by deterministic rules after you.""";

    public record ContractView(Long id, String ref, String workerName, String supplierName, String position,
                               LocalDate startDate, LocalDate endDate, BigDecimal hourlyRate, BigDecimal weeklyHours,
                               boolean overtimeAllowed, String legalReason, String replacedEmployee,
                               List<String> attachedCertificates, String content, boolean aiDrafted, String status,
                               Instant signedByClientAt, Instant signedBySupplierAt, List<RuleResult> checks,
                               boolean blocking) {
    }

    public record ContractsView(Long missionId, boolean generated, boolean readOnly, int placements,
                                List<ContractView> contracts) {
    }

    private final MissionService missions;
    private final IntakeService intake;
    private final SourcingService sourcing;
    private final ContractRepository contracts;
    private final WorkerRepository workers;
    private final TenantService tenants;
    private final AiGateway gateway;
    private final ArtifactService artifacts;
    private final AuditService audit;

    public ContractService(MissionService missions, IntakeService intake, SourcingService sourcing,
                           ContractRepository contracts, WorkerRepository workers, TenantService tenants, AiGateway gateway,
                           ArtifactService artifacts, AuditService audit) {
        this.missions = missions;
        this.intake = intake;
        this.sourcing = sourcing;
        this.contracts = contracts;
        this.workers = workers;
        this.tenants = tenants;
        this.gateway = gateway;
        this.artifacts = artifacts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ContractsView view(Long missionId) {
        Mission m = missions.requireReached(missionId, Phase.CONTRACTS);
        return toView(m);
    }

    /** One contract per placement. The first one reproduces a supplier template default: end date one week too late. */
    public ContractsView generate(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.CONTRACTS);
        if (!contracts.findByMissionIdOrderByRefAsc(m.getId()).isEmpty()) {
            throw new ConflictException("Contracts were already generated for this mission");
        }
        Order order = intake.order(m.getId()).orElseThrow(() -> new ConflictException("The order is not confirmed"));
        List<Placement> placements = sourcing.placementsOf(m.getId());
        if (placements.isEmpty()) {
            throw new ConflictException("No placement to contract");
        }
        int n = 0;
        for (Placement p : placements) {
            n++;
            Worker w = workers.findById(p.workerId()).orElseThrow();
            List<String> attached = order.requiredCertifications().stream()
                    .filter(req -> w.getCertifications().stream().anyMatch(c -> c.equalsIgnoreCase(req)))
                    .toList();
            LocalDate end = n == 1 ? order.endDate().plusDays(7) : order.endDate();
            ContractTerms terms = new ContractTerms(order.position(), order.startDate(), end, order.hourlyRate(),
                    order.weeklyHours(), order.overtimeAllowed(), order.legalReason(), order.replacedEmployee(), attached);
            String ref = String.format("CT-%04d-%02d", m.getNumber(), n);
            Contract c = contracts.save(new Contract(m.getId(), p.id(), w.getId(), p.supplierId(), ref, terms));
            draft(m, c, w, order);
            syncArtifact(m, c);
        }
        audit.system(m.getId(), "contracts.generated", n + " contract(s) generated from the order and placements",
                Map.of("count", n));
        m.touch();
        return toView(m);
    }

    public ContractView fix(Long contractId, String ruleId) {
        Contract c = contracts.findById(contractId).orElseThrow(() -> new NotFoundException("Contract", contractId));
        Mission m = missions.requireCurrentPhase(c.getMissionId(), Phase.CONTRACTS);
        if (c.isSigned()) {
            throw new ConflictException("Contract " + c.getRef() + " is already signed");
        }
        Order order = intake.order(m.getId()).orElseThrow();
        Worker w = workers.findById(c.getWorkerId()).orElseThrow();
        ContractTerms fixed = ContractRulesEngine.fix(ruleId, c.terms(), order, w.getCertifications())
                .orElseThrow(() -> new ConflictException("Rule " + ruleId + " cannot be fixed automatically on " + c.getRef()));
        ContractTerms before = c.terms();
        c.apply(fixed);
        audit.human(m.getId(), "contract.fixed", "Applied fix " + ruleId + " on " + c.getRef(),
                Map.of("contract", c.getRef(), "rule", ruleId, "before", describe(before), "after", describe(fixed)));
        m.touch();
        syncArtifact(m, c);
        return toContractView(c, order);
    }

    /** Simulated e-signature by both parties. Refused while a blocking issue remains. */
    public ContractsView sign(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.CONTRACTS);
        List<Contract> all = contracts.findByMissionIdOrderByRefAsc(m.getId());
        if (all.isEmpty()) {
            throw new ConflictException("Generate the contracts first");
        }
        Order order = intake.order(m.getId()).orElseThrow();
        List<String> blocked = all.stream().filter(c -> ContractRulesEngine.hasBlockingIssue(checks(c, order)))
                .map(Contract::getRef).toList();
        if (!blocked.isEmpty()) {
            throw new ConflictException("Blocking compliance issue on " + String.join(", ", blocked),
                    Map.of("contracts", blocked));
        }
        for (Contract c : all) {
            if (!c.isSigned()) {
                c.sign();
                syncArtifact(m, c);
            }
        }
        audit.human(m.getId(), "contracts.signed", all.size() + " contract(s) signed by client and supplier (simulated e-signature)",
                Map.of("contracts", all.stream().map(Contract::getRef).toList()));
        m.touch();
        return toView(m);
    }

    @Transactional(readOnly = true)
    public List<Contract> forMission(Long missionId) {
        return contracts.findByMissionIdOrderByRefAsc(missionId);
    }

    @Transactional(readOnly = true)
    public List<RuleResult> checks(Contract c, Order order) {
        List<String> certs = workers.findById(c.getWorkerId()).map(Worker::getCertifications).orElse(List.of());
        return ContractRulesEngine.check(c.terms(), order, certs);
    }

    // ------------------------------------------------------------------ internals

    private void draft(Mission m, Contract c, Worker w, Order order) {
        ContractTerms t = c.terms();
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("ref", c.getRef());
        facts.put("client", tenants.tenantName(m.getTenantId()));
        facts.put("supplier", tenants.supplierName(c.getSupplierId()));
        facts.put("worker", w.fullName());
        facts.put("position", t.position());
        facts.put("site", order.site());
        facts.put("startDate", t.startDate().toString());
        facts.put("endDate", t.endDate().toString());
        facts.put("schedule", order.schedule());
        facts.put("weeklyHours", t.weeklyHours().toPlainString());
        facts.put("hourlyRate", t.hourlyRate().toPlainString());
        facts.put("legalReason", t.legalReason());
        facts.put("replacedEmployee", t.replacedEmployee() == null ? "" : t.replacedEmployee());
        facts.put("certificates", t.attachedCertificates());
        StringBuilder user = new StringBuilder("Contract terms:\n");
        facts.forEach((k, v) -> user.append("- ").append(k).append(": ").append(v).append('\n'));
        AiResult<String> result = gateway.text(AiPrompt.of(FEATURE, m.getId(), SYSTEM_PROMPT, user.toString())
                .withFacts(facts).withMaxTokens(600));
        c.setContent(result.value().strip(), result.aiCallId());
    }

    private void syncArtifact(Mission m, Contract c) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Order order = intake.order(m.getId()).orElse(null);
        payload.put("worker", workers.findById(c.getWorkerId()).map(Worker::fullName).orElse(""));
        payload.put("supplier", tenants.supplierName(c.getSupplierId()));
        payload.put("start", c.getStartDate().toString());
        payload.put("end", c.getEndDate().toString());
        payload.put("hourlyRate", c.getHourlyRate());
        if (order != null) {
            payload.put("blockingIssues", checks(c, order).stream().filter(r -> !r.passed()).map(RuleResult::ruleId).toList());
        }
        artifacts.upsert(m.getId(), Phase.CONTRACTS, ArtifactService.CONTRACT, c.getRef(), c.getStatus(), payload);
    }

    private static String describe(ContractTerms t) {
        return t.startDate() + "→" + t.endDate() + ", €" + t.hourlyRate() + "/h, " + t.legalReason() + ", "
                + String.join("+", t.attachedCertificates());
    }

    private ContractsView toView(Mission m) {
        Order order = intake.order(m.getId()).orElse(null);
        List<ContractView> views = contracts.findByMissionIdOrderByRefAsc(m.getId()).stream()
                .map(c -> toContractView(c, order)).toList();
        return new ContractsView(m.getId(), !views.isEmpty(), m.getPhase() != Phase.CONTRACTS,
                sourcing.placementsOf(m.getId()).size(), views);
    }

    private ContractView toContractView(Contract c, Order order) {
        ContractTerms t = c.terms();
        List<RuleResult> checks = order == null ? List.of() : checks(c, order);
        return new ContractView(c.getId(), c.getRef(), workers.findById(c.getWorkerId()).map(Worker::fullName).orElse(""),
                tenants.supplierName(c.getSupplierId()), t.position(), t.startDate(), t.endDate(), t.hourlyRate(),
                t.weeklyHours(), t.overtimeAllowed(), t.legalReason(), t.replacedEmployee(), t.attachedCertificates(),
                c.getContent(), c.getContentAiCallId() != null, c.getStatus(), c.getSignedByClientAt(),
                c.getSignedBySupplierAt(), checks, ContractRulesEngine.hasBlockingIssue(checks));
    }
}
