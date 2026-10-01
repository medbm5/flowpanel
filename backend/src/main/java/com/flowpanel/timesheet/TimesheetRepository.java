package com.flowpanel.timesheet;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {

    List<Timesheet> findByMissionIdOrderByWeekStartAscWorkerIdAsc(Long missionId);
}
