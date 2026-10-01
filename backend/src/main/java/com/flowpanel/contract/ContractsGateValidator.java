package com.flowpanel.contract;

import com.flowpanel.intake.IntakeService;
import com.flowpanel.intake.Order;
import com.flowpanel.mission.Mission;
import com.flowpanel.mission.Phase;
import com.flowpanel.mission.gate.GateCheck;
import com.flowpanel.mission.gate.GateValidator;
import com.flowpanel.sourcing.SourcingService;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Contracts are done when every placement has a contract, no blocking issue remains, and all are signed. */
@Component
public class ContractsGateValidator implements GateValidator {

    private final ContractService contracts;
    private final SourcingService sourcing;
    private final IntakeService intake;

    public ContractsGateValidator(@Lazy ContractService contracts, @Lazy SourcingService sourcing, @Lazy IntakeService intake) {
        this.contracts = contracts;
        this.sourcing = sourcing;
        this.intake = intake;
    }

    @Override
    public Phase phase() {
        return Phase.CONTRACTS;
    }

    @Override
    public List<GateCheck> check(Mission mission) {
        List<Contract> all = contracts.forMission(mission.getId());
        int placements = sourcing.placementsOf(mission.getId()).size();
        Order order = intake.order(mission.getId()).orElse(null);
        long blocking = order == null ? 0 : all.stream()
                .filter(c -> ContractRulesEngine.hasBlockingIssue(contracts.checks(c, order))).count();
        boolean generated = !all.isEmpty() && all.size() == placements;
        boolean signed = generated && all.stream().allMatch(Contract::isSigned);
        return List.of(
                GateCheck.of("generated", "Contracts generated (" + all.size() + "/" + placements + ")", generated,
                        "Generate the contracts"),
                new GateCheck("no-blocking", blocking == 0 ? "No blocking compliance issue"
                        : blocking + " contract(s) with a blocking issue", generated && blocking == 0,
                        "Fix the blocking compliance issue", generated && blocking > 0),
                GateCheck.of("signed", "All contracts signed by both parties", signed, "Sign the contracts"));
    }
}
