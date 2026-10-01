package com.flowpanel.timesheet;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimesheetAnomalyRepository extends JpaRepository<TimesheetAnomaly, Long> {

    List<TimesheetAnomaly> findByMissionIdOrderByIdAsc(Long missionId);

    List<TimesheetAnomaly> findByTimesheetId(Long timesheetId);
}
