package com.flowpanel.tenant;

import com.flowpanel.auth.CurrentUser;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant and supplier lookups, scoped to the caller. Anything outside the caller's scope is reported as
 * not found (404), never forbidden, so a caller cannot probe which ids exist.
 */
@Service
@Transactional(readOnly = true)
public class TenantService {

    public record TenantView(Long id, String code, String name, String sector) {
    }

    public record SupplierView(Long id, String code, String name) {
    }

    private final TenantRepository tenants;
    private final SupplierRepository suppliers;
    private final RequestContext context;

    public TenantService(TenantRepository tenants, SupplierRepository suppliers, RequestContext context) {
        this.tenants = tenants;
        this.suppliers = suppliers;
        this.context = context;
    }

    public TenantView getTenant(Long id) {
        CurrentUser user = context.current();
        boolean visible = switch (user.role()) {
            case ADMIN -> true;
            case BUYER -> id.equals(user.tenantId());
            case SUPPLIER -> suppliers.isInPanel(id, user.supplierId());
        };
        Tenant tenant = tenants.findById(id).filter(t -> visible).orElseThrow(() -> new NotFoundException("Tenant", id));
        return toView(tenant);
    }

    public List<SupplierView> listSuppliers() {
        CurrentUser user = context.current();
        List<Supplier> result = switch (user.role()) {
            case ADMIN -> suppliers.findAll();
            case BUYER -> suppliers.findPanel(user.tenantId());
            case SUPPLIER -> suppliers.findById(user.supplierId()).stream().toList();
        };
        return result.stream().map(TenantService::toView).toList();
    }

    public SupplierView getSupplier(Long id) {
        CurrentUser user = context.current();
        boolean visible = switch (user.role()) {
            case ADMIN -> true;
            case BUYER -> suppliers.isInPanel(user.tenantId(), id);
            case SUPPLIER -> id.equals(user.supplierId());
        };
        Supplier supplier = suppliers.findById(id).filter(s -> visible)
                .orElseThrow(() -> new NotFoundException("Supplier", id));
        return toView(supplier);
    }

    public boolean isInPanel(Long tenantId, Long supplierId) {
        return suppliers.isInPanel(tenantId, supplierId);
    }

    public List<Supplier> panel(Long tenantId) {
        return suppliers.findPanel(tenantId);
    }

    public String tenantName(Long tenantId) {
        return tenants.findById(tenantId).map(Tenant::getName).orElse("");
    }

    public String supplierName(Long supplierId) {
        return suppliers.findById(supplierId).map(Supplier::getName).orElse("");
    }

    private static TenantView toView(Tenant t) {
        return new TenantView(t.getId(), t.getCode(), t.getName(), t.getSector());
    }

    private static SupplierView toView(Supplier s) {
        return new SupplierView(s.getId(), s.getCode(), s.getName());
    }
}
