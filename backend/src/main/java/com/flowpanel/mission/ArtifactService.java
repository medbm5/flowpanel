package com.flowpanel.mission;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Artifacts are always written by feature services after they have checked tenant scope and the current phase. */
@Service
@Transactional
public class ArtifactService {

    public static final String ORDER = "ORDER";
    public static final String SHORTLIST = "SHORTLIST";
    public static final String CONTRACT = "CONTRACT";
    public static final String TIMESHEETS = "TIMESHEETS";
    public static final String INVOICE = "INVOICE";
    public static final String CREDIT_NOTE = "CREDIT_NOTE";
    public static final String SUMMARY = "SUMMARY";

    private final ArtifactRepository repository;

    public ArtifactService(ArtifactRepository repository) {
        this.repository = repository;
    }

    public Artifact upsert(Long missionId, Phase phase, String type, String ref, String status, Map<String, Object> payload) {
        Optional<Artifact> existing = repository.findByMissionIdAndTypeAndRef(missionId, type, ref);
        if (existing.isPresent()) {
            existing.get().update(status, payload);
            return existing.get();
        }
        return repository.save(new Artifact(missionId, phase, type, ref, status, payload));
    }

    public void updateStatus(Long missionId, String type, String status) {
        repository.findByMissionIdAndType(missionId, type).forEach(a -> a.update(status, null));
    }

    public void delete(Long missionId, String type, String ref) {
        repository.findByMissionIdAndTypeAndRef(missionId, type, ref).ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public List<Artifact> forMission(Long missionId) {
        return repository.findByMissionIdOrderByCreatedAtAscIdAsc(missionId);
    }

    @Transactional(readOnly = true)
    public List<Artifact> byType(Long missionId, String type) {
        return repository.findByMissionIdAndType(missionId, type);
    }
}
