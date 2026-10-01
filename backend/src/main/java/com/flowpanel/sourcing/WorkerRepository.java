package com.flowpanel.sourcing;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    List<Worker> findBySupplierIdInOrderByIdAsc(Collection<Long> supplierIds);
}
