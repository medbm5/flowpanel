package com.flowpanel.mission;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhaseCompletionRepository extends JpaRepository<PhaseCompletion, Long> {

    List<PhaseCompletion> findByMissionIdOrderByFinalizedAtAsc(Long missionId);
}
