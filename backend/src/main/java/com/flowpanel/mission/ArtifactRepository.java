package com.flowpanel.mission;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    List<Artifact> findByMissionIdOrderByCreatedAtAscIdAsc(Long missionId);

    List<Artifact> findByMissionIdAndType(Long missionId, String type);

    Optional<Artifact> findByMissionIdAndTypeAndRef(Long missionId, String type, String ref);
}
