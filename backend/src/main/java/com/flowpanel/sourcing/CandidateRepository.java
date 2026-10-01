package com.flowpanel.sourcing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findByMissionIdOrderByEligibleDescScoreDesc(Long missionId);

    List<Candidate> findByMissionIdAndSupplierIdOrderByEligibleDescScoreDesc(Long missionId, Long supplierId);

    Optional<Candidate> findByIdAndMissionId(Long id, Long missionId);
}
