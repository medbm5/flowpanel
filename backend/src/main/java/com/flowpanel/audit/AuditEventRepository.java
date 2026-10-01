package com.flowpanel.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByTenantIdAndMissionIdOrderByCreatedAtDescIdDesc(Long tenantId, Long missionId);

    List<AuditEvent> findByActionOrderByIdDesc(String action);
}
