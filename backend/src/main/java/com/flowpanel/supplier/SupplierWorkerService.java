package com.flowpanel.supplier;

import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.RequestContext;
import com.flowpanel.common.BadRequestException;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.sourcing.Geo;
import com.flowpanel.sourcing.Worker;
import com.flowpanel.sourcing.WorkerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The staffing agency's own worker pool: only the caller's supplier, never another agency's workers. */
@Service
@Transactional
public class SupplierWorkerService {

    public record WorkerForm(String firstName, String lastName, String email, String phone, String city, List<String> skills,
                             List<String> certifications, Integer experienceYears, LocalDate availableFrom, LocalDate availableTo,
                             String profile) {
    }

    public record WorkerView(Long id, String firstName, String lastName, String email, String phone, String city,
                             List<String> skills, List<String> certifications, int experienceYears, LocalDate availableFrom,
                             LocalDate availableTo, String profile, int activePlacements, LocalDate placedUntil) {
    }

    public record PoolView(List<WorkerView> workers, List<String> cities) {
    }

    private final WorkerRepository workers;
    private final RequestContext context;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public SupplierWorkerService(WorkerRepository workers, RequestContext context, AuditService audit, JdbcTemplate jdbc) {
        this.workers = workers;
        this.context = context;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PoolView pool() {
        Long supplierId = context.requireSupplierId();
        List<WorkerView> views = workers.findBySupplierIdInOrderByIdAsc(List.of(supplierId)).stream().map(this::toView).toList();
        return new PoolView(views, Geo.cities());
    }

    public WorkerView create(WorkerForm form) {
        Long supplierId = context.requireSupplierId();
        Worker w = workers.save(new Worker(supplierId, toData(form)));
        audit.human(null, "worker.created", "Added " + w.fullName() + " to the worker pool", Map.of("workerId", w.getId()));
        return toView(w);
    }

    public WorkerView update(Long workerId, WorkerForm form) {
        Worker w = mine(workerId);
        w.update(toData(form));
        audit.human(null, "worker.updated", "Updated the profile of " + w.fullName(), Map.of("workerId", w.getId()));
        return toView(w);
    }

    /** A worker of the caller's agency, or 404. */
    @Transactional(readOnly = true)
    public Worker mine(Long workerId) {
        Long supplierId = context.requireSupplierId();
        return workers.findById(workerId).filter(w -> w.getSupplierId().equals(supplierId))
                .orElseThrow(() -> new NotFoundException("Worker", workerId));
    }

    private Worker.WorkerData toData(WorkerForm f) {
        List<String> errors = new ArrayList<>();
        if (blank(f.firstName())) errors.add("first name is required");
        if (blank(f.lastName())) errors.add("last name is required");
        if (blank(f.email()) || !f.email().contains("@")) errors.add("a valid email is required");
        if (blank(f.phone())) errors.add("phone is required");
        Geo.Point point = blank(f.city()) ? null : Geo.locate(f.city()).orElse(null);
        if (point == null) errors.add("city must be one of: " + String.join(", ", Geo.cities()));
        int years = f.experienceYears() == null ? 0 : f.experienceYears();
        if (years < 0 || years > 50) errors.add("experience must be between 0 and 50 years");
        if (f.availableFrom() == null) errors.add("available from is required");
        if (f.availableFrom() != null && f.availableTo() != null && f.availableTo().isBefore(f.availableFrom())) {
            errors.add("available until must be after available from");
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException("Invalid worker: " + String.join("; ", errors), Map.of("errors", errors));
        }
        List<String> skills = clean(f.skills());
        List<String> certs = clean(f.certifications());
        String profile = blank(f.profile())
                ? String.join(", ", skills) + (certs.isEmpty() ? "" : ". " + String.join(", ", certs)) + "." : f.profile().strip();
        return new Worker.WorkerData(f.firstName().strip(), f.lastName().strip(), f.email().strip(), f.phone().strip(),
                f.city().strip(), scale(point.lat()), scale(point.lon()), skills, certs, years, f.availableFrom(), f.availableTo(),
                profile);
    }

    private WorkerView toView(Worker w) {
        Map<String, Object> placed = jdbc.queryForMap("select count(*) as n, max(upper(period) - 1) as until from placement p "
                + "join mission m on m.id = p.mission_id where p.worker_id = ? and m.phase <> 'CLOSED'", w.getId());
        Object until = placed.get("until");
        return new WorkerView(w.getId(), w.getFirstName(), w.getLastName(), w.getEmail(), w.getPhone(), w.getCity(), w.getSkills(),
                w.getCertifications(), w.getExperienceYears(), w.getAvailableFrom(), w.getAvailableTo(), w.getProfile(),
                ((Number) placed.get("n")).intValue(),
                until instanceof java.sql.Date d ? d.toLocalDate() : null);
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::strip).filter(v -> !v.isEmpty()).distinct().toList();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static BigDecimal scale(double v) {
        return BigDecimal.valueOf(v).setScale(5, RoundingMode.HALF_UP);
    }
}
