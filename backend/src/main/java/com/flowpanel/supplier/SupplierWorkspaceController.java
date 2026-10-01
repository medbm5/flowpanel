package com.flowpanel.supplier;

import com.flowpanel.contract.ContractService.ContractView;
import com.flowpanel.invoice.InvoiceService.InvoiceView;
import com.flowpanel.supplier.SupplierWorkerService.PoolView;
import com.flowpanel.supplier.SupplierWorkerService.WorkerForm;
import com.flowpanel.supplier.SupplierWorkerService.WorkerView;
import com.flowpanel.supplier.SupplierWorkspaceService.Dashboard;
import com.flowpanel.supplier.SupplierWorkspaceService.TimesheetUpdate;
import com.flowpanel.supplier.SupplierWorkspaceService.Workspace;
import com.flowpanel.timesheet.TimesheetService.TimesheetView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Supplier (staffing agency) actions. All endpoints require the SUPPLIER role and act on the caller's agency only. */
@RestController
@Tag(name = "supplier")
public class SupplierWorkspaceController {

    public record ProposeRequest(Long workerId) {
    }

    private final SupplierWorkspaceService workspace;
    private final SupplierWorkerService workers;

    public SupplierWorkspaceController(SupplierWorkspaceService workspace, SupplierWorkerService workers) {
        this.workspace = workspace;
        this.workers = workers;
    }

    @GetMapping("/supplier/dashboard")
    public Dashboard dashboard() {
        return workspace.dashboard();
    }

    @GetMapping("/supplier/orders/{missionId}/workspace")
    public Workspace order(@PathVariable Long missionId) {
        return workspace.workspace(missionId);
    }

    @PostMapping("/supplier/orders/{missionId}/proposals")
    public Workspace propose(@PathVariable Long missionId, @RequestBody ProposeRequest request) {
        return workspace.propose(missionId, request.workerId());
    }

    @DeleteMapping("/supplier/orders/{missionId}/proposals/{candidateId}")
    public Workspace withdraw(@PathVariable Long missionId, @PathVariable Long candidateId) {
        return workspace.withdraw(missionId, candidateId);
    }

    @PostMapping("/supplier/contracts/{contractId}/sign")
    public ContractView sign(@PathVariable Long contractId) {
        return workspace.signContract(contractId);
    }

    @PutMapping("/supplier/timesheets/{timesheetId}")
    public TimesheetView updateTimesheet(@PathVariable Long timesheetId, @RequestBody TimesheetUpdate update) {
        return workspace.updateTimesheet(timesheetId, update);
    }

    @PostMapping("/supplier/invoices/{invoiceId}/credit-note")
    public InvoiceView creditNote(@PathVariable Long invoiceId) {
        return workspace.creditNote(invoiceId);
    }

    @GetMapping("/supplier/workers")
    public PoolView workers() {
        return workers.pool();
    }

    @PostMapping("/supplier/workers")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkerView createWorker(@RequestBody WorkerForm form) {
        return workers.create(form);
    }

    @PutMapping("/supplier/workers/{workerId}")
    public WorkerView updateWorker(@PathVariable Long workerId, @RequestBody WorkerForm form) {
        return workers.update(workerId, form);
    }
}
