package com.flowpanel.supplier;

import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.contract.Contract;
import com.flowpanel.contract.ContractService;
import com.flowpanel.contract.ContractService.ContractView;
import com.flowpanel.intake.IntakeService;
import com.flowpanel.intake.Order;
import com.flowpanel.invoice.Invoice;
import com.flowpanel.invoice.InvoiceService;
import com.flowpanel.invoice.InvoiceService.InvoiceView;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.sourcing.Candidate;
import com.flowpanel.sourcing.CandidateRepository;
import com.flowpanel.sourcing.SourcingService;
import com.flowpanel.sourcing.SourcingService.CandidateView;
import com.flowpanel.sourcing.SupplierPortalService;
import com.flowpanel.sourcing.SupplierPortalService.OrderSummary;
import com.flowpanel.sourcing.SupplierPortalService.PlacementView;
import com.flowpanel.sourcing.Worker;
import com.flowpanel.timesheet.Timesheet;
import com.flowpanel.timesheet.TimesheetService;
import com.flowpanel.timesheet.TimesheetService.AnomalyView;
import com.flowpanel.timesheet.TimesheetService.TimesheetView;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything a staffing agency does on an order published to it: propose and withdraw its own workers, sign its
 * contracts, submit or correct its timesheets, issue credit notes. Every query and action is limited to the caller's
 * supplier; another agency's proposals, contracts, hours and invoices are never returned.
 */
@Service
@Transactional
public class SupplierWorkspaceService {

    public record Terms(String position, Integer quantity, String site, String startDate, String endDate, String schedule,
                        BigDecimal weeklyHours, BigDecimal hourlyRate, String legalReason, List<String> requiredCertifications,
                        boolean overtimeAllowed) {
    }

    public record Workspace(OrderSummary order, Terms terms, boolean canPropose, boolean canSignContracts,
                            boolean canEditTimesheets, boolean canIssueCreditNotes, List<CandidateView> proposals,
                            List<PlacementView> placements, List<ContractView> contracts, List<TimesheetView> timesheets,
                            List<AnomalyView> anomalies, List<InvoiceView> invoices) {
    }

    public record TodoItem(Long missionId, String missionRef, String client, String kind, String label) {
    }

    public record Dashboard(int openOrders, int ordersToStaff, int activePlacements, int contractsToSign,
                            int timesheetsToFix, int invoicesToCredit, int workers, List<TodoItem> todo) {
    }

    public record TimesheetUpdate(List<BigDecimal> dailyHours) {
    }

    private final RequestContext context;
    private final SupplierPortalService portal;
    private final SupplierWorkerService pool;
    private final IntakeService intake;
    private final SourcingService sourcing;
    private final CandidateRepository candidates;
    private final ContractService contracts;
    private final TimesheetService timesheets;
    private final InvoiceService invoices;
    private final JdbcTemplate jdbc;

    public SupplierWorkspaceService(RequestContext context, SupplierPortalService portal, SupplierWorkerService pool,
                                    IntakeService intake, SourcingService sourcing, CandidateRepository candidates,
                                    ContractService contracts, TimesheetService timesheets, InvoiceService invoices,
                                    JdbcTemplate jdbc) {
        this.context = context;
        this.portal = portal;
        this.pool = pool;
        this.intake = intake;
        this.sourcing = sourcing;
        this.candidates = candidates;
        this.contracts = contracts;
        this.timesheets = timesheets;
        this.invoices = invoices;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Workspace workspace(Long missionId) {
        Long supplierId = context.requireSupplierId();
        Mission m = portal.publishedTo(missionId, supplierId);
        var detail = portal.order(missionId);
        Order o = intake.order(m.getId()).orElse(null);
        Terms terms = o == null ? null : new Terms(o.position(), o.quantity(), o.site(), String.valueOf(o.startDate()),
                String.valueOf(o.endDate()), o.schedule(), o.weeklyHours(), o.hourlyRate(), o.legalReason(),
                o.requiredCertifications(), o.overtimeAllowed());
        List<ContractView> myContracts = contracts.forMission(m.getId()).stream()
                .filter(c -> c.getSupplierId().equals(supplierId)).map(contracts::view).toList();
        List<InvoiceView> myInvoices = invoices.forMission(m.getId()).stream()
                .filter(i -> i.getSupplierId().equals(supplierId)).map(invoices::view).toList();
        return new Workspace(detail.order(), terms, m.getPhase() == Phase.SOURCING, m.getPhase() == Phase.CONTRACTS,
                m.getPhase() == Phase.TIMESHEETS, m.getPhase() == Phase.INVOICE, detail.myProposals(), detail.myPlacements(),
                myContracts, timesheets.supplierViews(m.getId(), supplierId), timesheets.supplierAnomalies(m.getId(), supplierId),
                myInvoices);
    }

    public Workspace propose(Long missionId, Long workerId) {
        Long supplierId = context.requireSupplierId();
        Mission m = portal.publishedTo(missionId, supplierId);
        if (m.getPhase() != Phase.SOURCING) {
            throw new com.flowpanel.common.ConflictException("Proposals are closed: the order is in phase " + m.getPhase().label());
        }
        Worker w = pool.mine(workerId);
        sourcing.proposeBySupplier(m, w);
        return workspace(missionId);
    }

    public Workspace withdraw(Long missionId, Long candidateId) {
        Long supplierId = context.requireSupplierId();
        Mission m = portal.publishedTo(missionId, supplierId);
        if (m.getPhase() != Phase.SOURCING) {
            throw new com.flowpanel.common.ConflictException("Proposals are closed: the order is in phase " + m.getPhase().label());
        }
        Candidate c = candidates.findByIdAndMissionId(candidateId, m.getId())
                .filter(x -> x.getSupplierId().equals(supplierId))
                .orElseThrow(() -> new NotFoundException("Proposal", candidateId));
        sourcing.withdrawBySupplier(m, c);
        return workspace(missionId);
    }

    public ContractView signContract(Long contractId) {
        Long supplierId = context.requireSupplierId();
        Contract c = contracts.find(contractId).filter(x -> x.getSupplierId().equals(supplierId))
                .orElseThrow(() -> new NotFoundException("Contract", contractId));
        Mission m = portal.publishedTo(c.getMissionId(), supplierId);
        return contracts.signBySupplier(m, c);
    }

    public TimesheetView updateTimesheet(Long timesheetId, TimesheetUpdate update) {
        Long supplierId = context.requireSupplierId();
        Timesheet t = timesheets.find(timesheetId).filter(x -> x.getSupplierId().equals(supplierId))
                .orElseThrow(() -> new NotFoundException("Timesheet", timesheetId));
        Mission m = portal.publishedTo(t.getMissionId(), supplierId);
        return timesheets.updateBySupplier(m, t, update == null ? null : update.dailyHours());
    }

    public InvoiceView creditNote(Long invoiceId) {
        Long supplierId = context.requireSupplierId();
        Invoice i = invoices.find(invoiceId).filter(x -> x.getSupplierId().equals(supplierId))
                .orElseThrow(() -> new NotFoundException("Invoice", invoiceId));
        Mission m = portal.publishedTo(i.getMissionId(), supplierId);
        return invoices.creditNoteBySupplier(m, i);
    }

    /** What the agency has to do next, across all its clients. */
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Long supplierId = context.requireSupplierId();
        List<OrderSummary> orders = portal.orders();
        List<TodoItem> todo = new java.util.ArrayList<>();
        int toStaff = 0;
        for (OrderSummary o : orders) {
            if (o.phase() == Phase.SOURCING && o.myPlacements() < (o.quantity() == null ? 0 : o.quantity())) {
                toStaff++;
                todo.add(new TodoItem(o.missionId(), o.ref(), o.client(), "PROPOSE",
                        o.myProposals() == 0 ? "Propose candidates for " + o.title() : "Strengthen your shortlist for " + o.title()));
            }
        }
        Integer toSign = jdbc.queryForObject("""
                select count(*) from contract c join mission m on m.id = c.mission_id
                where c.supplier_id = ? and c.signed_by_supplier_at is null and m.phase = 'CONTRACTS'
                """, Integer.class, supplierId);
        jdbc.query("""
                select distinct m.id, m.ref, t.name from contract c join mission m on m.id = c.mission_id join tenant t on t.id = m.tenant_id
                where c.supplier_id = ? and c.signed_by_supplier_at is null and m.phase = 'CONTRACTS'
                """, rs -> {
            todo.add(new TodoItem(rs.getLong(1), rs.getString(2), rs.getString(3), "SIGN", "Sign your contracts"));
        }, supplierId);
        Integer toFix = jdbc.queryForObject("""
                select count(distinct t.id) from timesheet t join timesheet_anomaly a on a.timesheet_id = t.id
                join mission m on m.id = t.mission_id where t.supplier_id = ? and a.status = 'OPEN' and m.phase = 'TIMESHEETS'
                """, Integer.class, supplierId);
        jdbc.query("""
                select distinct m.id, m.ref, tn.name from timesheet t join timesheet_anomaly a on a.timesheet_id = t.id
                join mission m on m.id = t.mission_id join tenant tn on tn.id = m.tenant_id
                where t.supplier_id = ? and a.status = 'OPEN' and m.phase = 'TIMESHEETS'
                """, rs -> {
            todo.add(new TodoItem(rs.getLong(1), rs.getString(2), rs.getString(3), "TIMESHEET",
                    "A timesheet was flagged — check or correct the hours"));
        }, supplierId);
        Integer toCredit = jdbc.queryForObject("""
                select count(*) from invoice i join mission m on m.id = i.mission_id
                where i.supplier_id = ? and i.status = 'MISMATCH' and m.phase = 'INVOICE'
                """, Integer.class, supplierId);
        jdbc.query("""
                select i.mission_id, m.ref, t.name, i.ref from invoice i join mission m on m.id = i.mission_id
                join tenant t on t.id = m.tenant_id where i.supplier_id = ? and i.status = 'MISMATCH' and m.phase = 'INVOICE'
                """, rs -> {
            todo.add(new TodoItem(rs.getLong(1), rs.getString(2), rs.getString(3), "CREDIT_NOTE",
                    "Invoice " + rs.getString(4) + " does not match — issue a credit note"));
        }, supplierId);
        Integer active = jdbc.queryForObject("""
                select count(*) from placement p join mission m on m.id = p.mission_id
                where p.supplier_id = ? and m.phase <> 'CLOSED'
                """, Integer.class, supplierId);
        int open = (int) orders.stream().filter(o -> o.phase() != Phase.CLOSED).count();
        return new Dashboard(open, toStaff, active, toSign, toFix, toCredit, pool.pool().workers().size(), todo);
    }
}
