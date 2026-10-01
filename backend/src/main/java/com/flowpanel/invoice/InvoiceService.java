package com.flowpanel.invoice;

import com.flowpanel.ai.AiGateway;
import com.flowpanel.ai.AiPrompt;
import com.flowpanel.ai.AiResult;
import com.flowpanel.audit.ActorKind;
import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.BadRequestException;
import com.flowpanel.common.ConflictException;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.contract.Contract;
import com.flowpanel.contract.ContractService;
import com.flowpanel.mission.ArtifactService;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionService;
import com.flowpanel.mission.Phase;
import com.flowpanel.sourcing.Worker;
import com.flowpanel.sourcing.WorkerRepository;
import com.flowpanel.tenant.Supplier;
import com.flowpanel.tenant.TenantService;
import com.flowpanel.timesheet.TimesheetService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvoiceService {

    public static final String EXTRACT_FEATURE = "invoice.extract";
    public static final String MESSAGE_FEATURE = "invoice.message";
    /** Synthetic overbilling: the first invoice of a mission bills 3 h more than approved on its first line. */
    static final BigDecimal OVERBILLED_HOURS = new BigDecimal("3");

    static final String EXTRACT_PROMPT = """
            You extract the lines of a French temporary staffing invoice from its text. Return JSON matching the schema:
            invoice number, supplier name, one line per worker (name exactly as written, hours, hourly rate, amount excluding
            VAT) and the total excluding VAT. Numbers use a dot as decimal separator and no thousands separator. Never
            compute or correct values: copy what the invoice says.""";

    static final String MESSAGE_PROMPT = """
            You draft a short, polite email in French from a staffing buyer to a supplier, asking for a credit note
            ("avoir") after a three-way match found that the invoice exceeds the approved hours or the contract rate. Use only
            the facts given (invoice number, mission, gaps, credit note amount, signature). Do not threaten, do not add facts.""";

    public record InvoiceView(Long id, String ref, String supplierName, String status, InvoiceLines extraction,
                              boolean aiExtracted, ThreeWayMatcher.Result match, String aiMessageDraft,
                              String creditNoteRef, BigDecimal creditNoteAmount, Map<String, Object> creditNote,
                              String extractedText) {
    }

    public record InvoicesView(Long missionId, boolean received, boolean readOnly, List<InvoiceView> invoices,
                               BigDecimal expectedTotal, BigDecimal invoicedTotal, BigDecimal creditNotes) {
    }

    private final MissionService missions;
    private final ContractService contracts;
    private final TimesheetService timesheets;
    private final InvoiceRepository invoices;
    private final WorkerRepository workers;
    private final TenantService tenants;
    private final AiGateway gateway;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final RequestContext context;

    public InvoiceService(MissionService missions, ContractService contracts, TimesheetService timesheets,
                          InvoiceRepository invoices, WorkerRepository workers, TenantService tenants, AiGateway gateway,
                          ArtifactService artifacts, AuditService audit, RequestContext context) {
        this.missions = missions;
        this.contracts = contracts;
        this.timesheets = timesheets;
        this.invoices = invoices;
        this.workers = workers;
        this.tenants = tenants;
        this.gateway = gateway;
        this.artifacts = artifacts;
        this.audit = audit;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public InvoicesView view(Long missionId) {
        Mission m = missions.requireReached(missionId, Phase.INVOICE);
        return toView(m);
    }

    @Transactional(readOnly = true)
    public List<Invoice> forMission(Long missionId) {
        return invoices.findByMissionIdOrderByIdAsc(missionId);
    }

    @Transactional(readOnly = true)
    public Invoice pdfOf(Long invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new NotFoundException("Invoice", invoiceId));
        var user = context.current();
        if (user.isSupplier()) {
            // A staffing agency may download its own invoices only.
            if (!invoice.getSupplierId().equals(user.supplierId())) {
                throw new NotFoundException("Invoice", invoiceId);
            }
            return invoice;
        }
        missions.getForTenant(invoice.getMissionId());
        return invoice;
    }

    /**
     * Each supplier of the mission sends its invoice (a synthetic PDF that overbills a few hours). The text is
     * extracted with PDFBox, the lines by the LLM, then the deterministic three-way match runs.
     */
    public InvoicesView receive(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.INVOICE);
        if (!invoices.findByMissionIdOrderByIdAsc(m.getId()).isEmpty()) {
            throw new ConflictException("Invoices were already received");
        }
        Map<Long, BigDecimal> approved = timesheets.approvedHoursByContract(m.getId());
        Map<Long, List<Contract>> bySupplier = contracts.forMission(m.getId()).stream()
                .collect(Collectors.groupingBy(Contract::getSupplierId, LinkedHashMap::new, Collectors.toList()));
        boolean overbilled = false;
        for (Map.Entry<Long, List<Contract>> entry : bySupplier.entrySet()) {
            Supplier supplier = tenants.panel(m.getTenantId()).stream().filter(s -> s.getId().equals(entry.getKey())).findFirst()
                    .orElseThrow();
            List<InvoicePdf.Line> lines = new ArrayList<>();
            LocalDate start = null;
            LocalDate end = null;
            for (Contract c : entry.getValue()) {
                BigDecimal hours = approved.getOrDefault(c.getId(), BigDecimal.ZERO);
                if (!overbilled) {
                    hours = hours.add(OVERBILLED_HOURS);
                    overbilled = true;
                }
                lines.add(new InvoicePdf.Line(workerName(c.getWorkerId()), hours, c.getHourlyRate()));
                start = start == null || c.getStartDate().isBefore(start) ? c.getStartDate() : start;
                end = end == null || c.getEndDate().isAfter(end) ? c.getEndDate() : end;
            }
            String ref = "INV-" + supplier.getCode().toUpperCase() + "-" + String.format("%04d", m.getNumber());
            byte[] pdf = InvoicePdf.render(new InvoicePdf.Document(ref, supplier.getName(), tenants.tenantName(m.getTenantId()),
                    m.getRef(), start, end, end.plusDays(3), lines));
            Invoice invoice = invoices.save(new Invoice(m.getId(), supplier.getId(), ref, pdf, InvoicePdf.text(pdf)));
            audit.record(ActorKind.SYSTEM, m.getTenantId(), m.getId(), "invoice.received",
                    supplier.getName() + " sent invoice " + ref, Map.of("invoice", ref));
            AiResult<InvoiceLines> extraction = extract(m.getId(), invoice.getExtractedText());
            invoice.extracted(extraction.value(), extraction.aiCallId());
            audit.ai(m.getId(), "invoice.extracted", "AI extracted " + extraction.value().lines().size() + " line(s) from " + ref,
                    Map.of("invoice", ref, "aiCallId", extraction.aiCallId()));
            rematch(m, invoice);
            syncArtifact(m, invoice);
        }
        m.touch();
        return toView(m);
    }

    /** Stateless extraction from invoice text (used by the eval runner). */
    public InvoiceLines extractText(String text) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("text is required");
        }
        return extract(null, text).value();
    }

    /** Simulates the supplier's credit note for the overbilled amount; the match then passes. */
    public InvoicesView requestCreditNote(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.INVOICE);
        List<Invoice> mismatched = invoices.findByMissionIdOrderByIdAsc(m.getId()).stream()
                .filter(i -> "MISMATCH".equals(i.getStatus())).toList();
        if (mismatched.isEmpty()) {
            throw new ConflictException("No invoice mismatch to credit");
        }
        for (Invoice invoice : mismatched) {
            audit.human(m.getId(), "invoice.credit-note.requested", "Sent the credit note request for " + invoice.getRef(),
                    Map.of("invoice", invoice.getRef()));
            issueCreditNote(m, invoice, ActorKind.SYSTEM);
        }
        m.touch();
        return toView(m);
    }

    /** The supplier issues the credit note itself from the portal (its own invoice, mission in INVOICE, mismatch). */
    public InvoiceView creditNoteBySupplier(Mission m, Invoice invoice) {
        if (m.getPhase() != Phase.INVOICE) {
            throw new ConflictException("Credit notes can only be issued during the Invoice phase");
        }
        if (!"MISMATCH".equals(invoice.getStatus())) {
            throw new ConflictException("Invoice " + invoice.getRef() + " has no mismatch to credit");
        }
        issueCreditNote(m, invoice, ActorKind.HUMAN);
        m.touch();
        return toInvoiceView(invoice);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Invoice> find(Long invoiceId) {
        return invoices.findById(invoiceId);
    }

    @Transactional(readOnly = true)
    public InvoiceView view(Invoice invoice) {
        return toInvoiceView(invoice);
    }

    /** Credit note for the overbilled amount of each mismatched line; the invoice is then matched again. */
    private void issueCreditNote(Mission m, Invoice invoice, ActorKind actor) {
        BigDecimal amount = invoice.getMatchResult().lines().stream().map(ThreeWayMatcher.LineResult::delta)
                .filter(d -> d != null && d.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> creditLines = invoice.getMatchResult().lines().stream()
                .filter(l -> l.delta() != null && l.delta().signum() > 0)
                .map(l -> Map.<String, Object>of("worker", l.workerName(), "amount", l.delta(), "reason", l.message()))
                .toList();
        String ref = "AV-" + invoice.getRef().substring(4);
        invoice.creditNote(ref, amount, Map.of("lines", creditLines));
        audit.record(actor, m.getTenantId(), m.getId(), "invoice.credit-note.received",
                tenants.supplierName(invoice.getSupplierId()) + " issued credit note " + ref + " (" + amount + " € HT)",
                Map.of("creditNote", ref, "amount", amount));
        rematch(m, invoice);
        artifacts.upsert(m.getId(), Phase.INVOICE, ArtifactService.CREDIT_NOTE, ref, "RECEIVED",
                Map.of("invoice", invoice.getRef(), "amount", amount, "lines", creditLines));
        syncArtifact(m, invoice);
    }

    public InvoicesView approve(Long missionId) {
        Mission m = missions.requireCurrentPhase(missionId, Phase.INVOICE);
        List<Invoice> all = invoices.findByMissionIdOrderByIdAsc(m.getId());
        if (all.isEmpty()) {
            throw new ConflictException("No invoice received yet");
        }
        List<String> unmatched = all.stream().filter(i -> !"MATCHED".equals(i.getStatus()) && !"APPROVED".equals(i.getStatus()))
                .map(Invoice::getRef).toList();
        if (!unmatched.isEmpty()) {
            throw new ConflictException("Three-way match does not pass for " + String.join(", ", unmatched),
                    Map.of("invoices", unmatched));
        }
        all.forEach(i -> {
            i.approve();
            syncArtifact(m, i);
        });
        audit.human(m.getId(), "invoice.approved", "Approved " + all.size() + " invoice(s) for payment",
                Map.of("invoices", all.stream().map(Invoice::getRef).toList()));
        m.touch();
        return toView(m);
    }

    /** Amount to pay for an invoice: invoiced total minus its credit note. */
    public static BigDecimal payable(Invoice i) {
        BigDecimal invoiced = i.getExtraction() == null ? BigDecimal.ZERO : i.getExtraction().totalExclTax();
        return i.getCreditNoteAmount() == null ? invoiced : invoiced.subtract(i.getCreditNoteAmount());
    }

    // ------------------------------------------------------------------ internals

    private AiResult<InvoiceLines> extract(Long missionId, String text) {
        return gateway.structured(AiPrompt.of(EXTRACT_FEATURE, missionId, EXTRACT_PROMPT, text).withMaxTokens(600),
                InvoiceLines.class);
    }

    /** Three-way match of the invoice net of its credit note; drafts the supplier message on a mismatch. */
    private void rematch(Mission m, Invoice invoice) {
        Map<Long, BigDecimal> approved = timesheets.approvedHoursByContract(m.getId());
        List<ThreeWayMatcher.Expected> expected = contracts.forMission(m.getId()).stream()
                .filter(c -> c.getSupplierId().equals(invoice.getSupplierId()))
                .map(c -> new ThreeWayMatcher.Expected(workerName(c.getWorkerId()), c.getHourlyRate(),
                        approved.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
        List<ThreeWayMatcher.Invoiced> lines = new ArrayList<>();
        Map<String, BigDecimal> credited = new LinkedHashMap<>();
        if (invoice.getCreditNote() != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> creditLines = (List<Map<String, Object>>) invoice.getCreditNote().get("lines");
            creditLines.forEach(l -> credited.put(String.valueOf(l.get("worker")), new BigDecimal(String.valueOf(l.get("amount")))));
        }
        for (InvoiceLines.Line l : invoice.getExtraction().lines()) {
            BigDecimal credit = credited.entrySet().stream().filter(e -> ThreeWayMatcher.sameName(e.getKey(), l.workerName()))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            if (credit == null) {
                lines.add(new ThreeWayMatcher.Invoiced(l.workerName(), l.hours(), l.hourlyRate(), l.amount()));
            } else {
                // The credit note brings the line back to the expected amount; net hours follow from the credited amount.
                BigDecimal netAmount = l.amount().subtract(credit);
                BigDecimal netHours = l.hourlyRate().signum() == 0 ? l.hours()
                        : netAmount.divide(l.hourlyRate(), 2, java.math.RoundingMode.HALF_UP);
                lines.add(new ThreeWayMatcher.Invoiced(l.workerName(), netHours, l.hourlyRate(), netAmount));
            }
        }
        ThreeWayMatcher.Result result = ThreeWayMatcher.match(expected, lines);
        invoice.matched(result);
        if (!result.matched() && invoice.getMessageDraft() == null) {
            draftMessage(m, invoice, result);
        }
    }

    private void draftMessage(Mission m, Invoice invoice, ThreeWayMatcher.Result result) {
        List<Map<String, Object>> gaps = result.lines().stream().filter(l -> l.status() != ThreeWayMatcher.Status.MATCH)
                .map(l -> Map.<String, Object>of("worker", l.workerName(), "detail", l.message(),
                        "delta", l.delta() == null ? "0" : l.delta().toPlainString()))
                .toList();
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("invoice", invoice.getRef());
        facts.put("mission", m.getRef());
        facts.put("gaps", gaps);
        facts.put("creditNote", result.overbilled().toPlainString());
        facts.put("buyer", context.current().displayName() + ", " + tenants.tenantName(m.getTenantId()));
        String user = "Invoice " + invoice.getRef() + " for mission " + m.getRef() + ". Gaps: " + gaps
                + ". Credit note requested: " + result.overbilled() + " EUR excl. VAT. Signature: " + facts.get("buyer");
        AiResult<String> draft = gateway.text(AiPrompt.of(MESSAGE_FEATURE, m.getId(), MESSAGE_PROMPT, user).withFacts(facts)
                .withMaxTokens(350));
        invoice.drafted(draft.value().strip(), draft.aiCallId());
    }

    private void syncArtifact(Mission m, Invoice i) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supplier", tenants.supplierName(i.getSupplierId()));
        payload.put("total", i.getExtraction() == null ? null : i.getExtraction().totalExclTax());
        payload.put("creditNote", i.getCreditNoteAmount());
        payload.put("payable", payable(i));
        payload.put("pdf", "/invoices/" + i.getId() + "/pdf");
        artifacts.upsert(m.getId(), Phase.INVOICE, ArtifactService.INVOICE, i.getRef(), i.getStatus(), payload);
    }

    private InvoiceView toInvoiceView(Invoice i) {
        return new InvoiceView(i.getId(), i.getRef(), tenants.supplierName(i.getSupplierId()), i.getStatus(), i.getExtraction(),
                i.getExtractionAiCallId() != null, i.getMatchResult(), i.getMessageDraft(), i.getCreditNoteRef(),
                i.getCreditNoteAmount(), i.getCreditNote(), i.getExtractedText());
    }

    private String workerName(Long workerId) {
        return workers.findById(workerId).map(Worker::fullName).orElse("");
    }

    private InvoicesView toView(Mission m) {
        List<Invoice> all = invoices.findByMissionIdOrderByIdAsc(m.getId());
        List<InvoiceView> views = all.stream().map(this::toInvoiceView).toList();
        BigDecimal expected = all.stream().filter(i -> i.getMatchResult() != null).map(i -> i.getMatchResult().expectedTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invoiced = all.stream().filter(i -> i.getExtraction() != null).map(i -> i.getExtraction().totalExclTax())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = all.stream().map(Invoice::getCreditNoteAmount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InvoicesView(m.getId(), !all.isEmpty(), m.getPhase() != Phase.INVOICE, views, expected, invoiced, credits);
    }
}
