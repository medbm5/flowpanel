package com.flowpanel.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByMissionIdOrderByIdAsc(Long missionId);

    List<Invoice> findBySupplierIdOrderByIdDesc(Long supplierId);
}
