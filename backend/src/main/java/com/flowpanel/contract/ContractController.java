package com.flowpanel.contract;

import com.flowpanel.contract.ContractService.ContractView;
import com.flowpanel.contract.ContractService.ContractsView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "contracts")
public class ContractController {

    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping("/missions/{id}/contracts")
    public ContractsView get(@PathVariable Long id) {
        return service.view(id);
    }

    @PostMapping("/missions/{id}/contracts/generate")
    public ContractsView generate(@PathVariable Long id) {
        return service.generate(id);
    }

    @PostMapping("/contracts/{contractId}/fix/{ruleId}")
    public ContractView fix(@PathVariable Long contractId, @PathVariable String ruleId) {
        return service.fix(contractId, ruleId);
    }

    @PostMapping("/missions/{id}/contracts/sign")
    public ContractsView sign(@PathVariable Long id) {
        return service.sign(id);
    }
}
