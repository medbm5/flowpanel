package com.flowpanel.invoice;

import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Invoice is done when invoices were received, the three-way match passes and they are approved. */
@Component
public class InvoiceGateValidator implements GateValidator {

    private final InvoiceService invoices;

    public InvoiceGateValidator(@Lazy InvoiceService invoices) {
        this.invoices = invoices;
    }

    @Override
    public Phase phase() {
        return Phase.INVOICE;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        List<Invoice> all = invoices.forMission(mission.getId());
        boolean received = !all.isEmpty();
        long mismatched = all.stream().filter(i -> "MISMATCH".equals(i.getStatus())).count();
        boolean approved = received && all.stream().allMatch(i -> "APPROVED".equals(i.getStatus()));
        return List.of(
                GateCheck.of("received", "Supplier invoices received", received, "Receive the supplier invoices"),
                new GateCheck("matched", mismatched == 0 ? "Three-way match passes" : mismatched + " invoice(s) do not match",
                        received && mismatched == 0, "Request a credit note for the mismatch", mismatched > 0),
                GateCheck.of("approved", "Invoices approved", approved, "Approve the invoices"));
    }
}
