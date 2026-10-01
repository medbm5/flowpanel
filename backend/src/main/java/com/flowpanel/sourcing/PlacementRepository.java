package com.flowpanel.sourcing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Placements use a Postgres {@code daterange} with an exclusion constraint, so they are accessed with plain SQL. */
@Repository
public class PlacementRepository {

    public record Placement(Long id, Long missionId, String missionRef, Long workerId, Long supplierId, Long candidateId,
                            LocalDate start, LocalDate end) {
    }

    private static final String SELECT = """
            select p.id, p.mission_id, m.ref, p.worker_id, p.supplier_id, p.candidate_id,
                   lower(p.period) as start_date, upper(p.period) - 1 as end_date
            from placement p join mission m on m.id = p.mission_id
            """;

    private final JdbcTemplate jdbc;

    public PlacementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a placement for the inclusive period [start, end]. Throws on double booking (SQLState 23P01). */
    public long insert(Long missionId, Long workerId, Long supplierId, Long candidateId, LocalDate start, LocalDate end,
                       Long createdBy) {
        return jdbc.queryForObject("""
                insert into placement (mission_id, worker_id, supplier_id, candidate_id, period, created_by)
                values (?, ?, ?, ?, daterange(?, ?, '[]'), ?) returning id
                """, Long.class, missionId, workerId, supplierId, candidateId, start, end, createdBy);
    }

    public int delete(Long missionId, Long candidateId) {
        return jdbc.update("delete from placement where mission_id = ? and candidate_id = ?", missionId, candidateId);
    }

    public List<Placement> forMission(Long missionId) {
        return jdbc.query(SELECT + " where p.mission_id = ? order by p.id", this::map, missionId);
    }

    public List<Placement> forWorkers(List<Long> workerIds) {
        if (workerIds.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", workerIds.stream().map(id -> "?").toList());
        return jdbc.query(SELECT + " where p.worker_id in (" + in + ")", this::map, workerIds.toArray());
    }

    public int count(Long missionId) {
        return jdbc.queryForObject("select count(*) from placement where mission_id = ?", Integer.class, missionId);
    }

    /** Looks up the placement blocking a worker, in a fresh transaction (the caller's one is aborted by the violation). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Placement> overlapping(Long workerId, LocalDate start, LocalDate end) {
        return jdbc.query(SELECT + " where p.worker_id = ? and p.period && daterange(?, ?, '[]') limit 1", this::map,
                workerId, start, end).stream().findFirst();
    }

    private Placement map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Placement(rs.getLong("id"), rs.getLong("mission_id"), rs.getString("ref"), rs.getLong("worker_id"),
                rs.getLong("supplier_id"), rs.getLong("candidate_id"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class));
    }
}
