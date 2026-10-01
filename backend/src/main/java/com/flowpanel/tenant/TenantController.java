package com.flowpanel.tenant;

import com.flowpanel.tenant.TenantService.SupplierView;
import com.flowpanel.tenant.TenantService.TenantView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @GetMapping("/tenants/{id}")
    public TenantView tenant(@PathVariable Long id) {
        return service.getTenant(id);
    }

    @GetMapping("/suppliers")
    public List<SupplierView> suppliers() {
        return service.listSuppliers();
    }

    @GetMapping("/suppliers/{id}")
    public SupplierView supplier(@PathVariable Long id) {
        return service.getSupplier(id);
    }
}
