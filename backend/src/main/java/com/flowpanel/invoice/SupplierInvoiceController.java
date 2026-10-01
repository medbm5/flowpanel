package com.flowpanel.invoice;

import com.flowpanel.auth.RequestContext;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.MissionRepository;
import com.flowpanel.tenant.TenantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Invoices of the caller's supplier only. */
@RestController
@Tag(name = "supplier")
public class SupplierInvoiceController {

    public record SupplierInvoice(Long id, String ref, String missionRef, String client, String status, BigDecimal total,
                                  String creditNoteRef, BigDecimal creditNoteAmount, BigDecimal payable) {
    }

    private final RequestContext context;
    private final InvoiceRepository invoices;
    private final MissionRepository missions;
    private final TenantService tenants;

    public SupplierInvoiceController(RequestContext context, InvoiceRepository invoices, MissionRepository missions,
                                     TenantService tenants) {
        this.context = context;
        this.invoices = invoices;
        this.missions = missions;
        this.tenants = tenants;
    }

    @GetMapping("/supplier/invoices")
    @Transactional(readOnly = true)
    public List<SupplierInvoice> invoices() {
        Long supplierId = context.requireSupplierId();
        return invoices.findBySupplierIdOrderByIdDesc(supplierId).stream().map(i -> {
            Mission m = missions.findById(i.getMissionId()).orElseThrow();
            return new SupplierInvoice(i.getId(), i.getRef(), m.getRef(), tenants.tenantName(m.getTenantId()), i.getStatus(),
                    i.getExtraction() == null ? null : i.getExtraction().totalExclTax(), i.getCreditNoteRef(),
                    i.getCreditNoteAmount(), InvoiceService.payable(i));
        }).toList();
    }
}
