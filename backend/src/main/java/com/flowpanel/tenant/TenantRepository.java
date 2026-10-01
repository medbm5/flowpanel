package com.flowpanel.tenant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    @Query(value = "select t.* from tenant t join tenant_supplier ts on ts.tenant_id = t.id "
            + "where ts.supplier_id = :supplierId order by t.id", nativeQuery = true)
    List<Tenant> findServedBySupplier(Long supplierId);
}
