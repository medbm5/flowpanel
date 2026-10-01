package com.flowpanel.mission;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    List<Mission> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<Mission> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mission m where m.id = :id and m.tenantId = :tenantId")
    Optional<Mission> lockByIdAndTenantId(Long id, Long tenantId);

    Optional<Mission> findByRef(String ref);

    @Query(value = "select nextval('mission_number_seq')", nativeQuery = true)
    long nextNumber();
}
