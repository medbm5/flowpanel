package com.flowpanel.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PhaseTransitionTest {

    private static final Set<String> ALLOWED = Set.of(
            "INTAKE>SOURCING", "SOURCING>CONTRACTS", "CONTRACTS>TIMESHEETS", "TIMESHEETS>INVOICE", "INVOICE>CLOSED");

    static Stream<Arguments> allPairs() {
        return Stream.of(Phase.values()).flatMap(from -> Stream.of(Phase.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void everyTransitionIsExplicitlyAllowedOrRejected(Phase from, Phase to) {
        boolean expected = ALLOWED.contains(from + ">" + to);
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);

        Mission mission = missionIn(from);
        if (expected) {
            mission.transitionTo(to);
            assertThat(mission.getPhase()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> mission.transitionTo(to)).isInstanceOf(IllegalPhaseTransitionException.class);
            assertThat(mission.getPhase()).isEqualTo(from);
        }
    }

    @Test
    void nextFollowsTheWorkflowAndClosedIsTerminal() {
        assertThat(Phase.INTAKE.next()).contains(Phase.SOURCING);
        assertThat(Phase.INVOICE.next()).contains(Phase.CLOSED);
        assertThat(Phase.CLOSED.next()).isEmpty();
        assertThat(Phase.CLOSED.allowedTargets()).isEmpty();
    }

    @Test
    void closingSetsClosedAt() {
        Mission mission = missionIn(Phase.INVOICE);
        mission.transitionTo(Phase.CLOSED);
        assertThat(mission.getClosedAt()).isNotNull();
    }

    private static Mission missionIn(Phase phase) {
        Mission mission = new Mission(1L, 1, "ORD-TEST-0001", "Test", "Site", null, "email", 1L);
        for (Phase p : Phase.values()) {
            if (mission.getPhase() == phase) {
                break;
            }
            mission.transitionTo(mission.getPhase().next().orElseThrow());
        }
        return mission;
    }
}
