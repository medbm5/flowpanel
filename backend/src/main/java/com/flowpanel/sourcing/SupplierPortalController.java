package com.flowpanel.sourcing;

import com.flowpanel.sourcing.SupplierPortalService.OrderDetail;
import com.flowpanel.sourcing.SupplierPortalService.OrderSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "supplier")
public class SupplierPortalController {

    private final SupplierPortalService service;

    public SupplierPortalController(SupplierPortalService service) {
        this.service = service;
    }

    @GetMapping("/supplier/orders")
    public List<OrderSummary> orders() {
        return service.orders();
    }

    @GetMapping("/supplier/orders/{missionId}")
    public OrderDetail order(@PathVariable Long missionId) {
        return service.order(missionId);
    }
}
