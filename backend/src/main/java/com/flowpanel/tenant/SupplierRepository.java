package com.flowpanel.tenant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    @Query(value = "select s.* from supplier s join tenant_supplier ts on ts.supplier_id = s.id "
            + "where ts.tenant_id = :tenantId order by s.id", nativeQuery = true)
    List<Supplier> findPanel(Long tenantId);

    @Query(value = "select count(*) > 0 from tenant_supplier where tenant_id = :tenantId and supplier_id = :supplierId",
            nativeQuery = true)
    boolean isInPanel(Long tenantId, Long supplierId);
}
