package com.flowpanel.auth;

import com.flowpanel.audit.AuditService;
import com.flowpanel.auth.AuthDtos.Me;
import com.flowpanel.auth.AuthDtos.NamedRef;
import com.flowpanel.auth.AuthDtos.Persona;
import com.flowpanel.common.NotFoundException;
import com.flowpanel.tenant.SupplierRepository;
import com.flowpanel.tenant.TenantRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final SupplierRepository suppliers;
    private final JwtService jwtService;
    private final RequestContext context;
    private final AuditService audit;

    public AuthService(AppUserRepository users, TenantRepository tenants, SupplierRepository suppliers,
                       JwtService jwtService, RequestContext context, AuditService audit) {
        this.users = users;
        this.tenants = tenants;
        this.suppliers = suppliers;
        this.jwtService = jwtService;
        this.context = context;
        this.audit = audit;
    }

    public record LoginResult(String token, Me me) {
    }

    @Transactional
    public LoginResult demoLogin(String persona) {
        AppUser user = users.findByPersona(persona).orElseThrow(() -> new NotFoundException("Persona", persona));
        String token = jwtService.issue(user);
        CurrentUser current = toCurrent(user);
        context.runAs(current, () -> audit.human(null, "auth.login", user.getDisplayName() + " signed in (demo persona)",
                Map.of("persona", persona)));
        return new LoginResult(token, toMe(user));
    }

    @Transactional(readOnly = true)
    public Me me() {
        CurrentUser current = context.current();
        AppUser user = users.findById(current.userId()).orElseThrow(() -> new NotFoundException("User", current.userId()));
        return toMe(user);
    }

    @Transactional(readOnly = true)
    public List<Persona> personas() {
        return users.findAllByOrderByIdAsc().stream()
                .map(u -> new Persona(u.getPersona(), u.getDisplayName(), u.getJobTitle(), u.getRole(), organization(u)))
                .toList();
    }

    private String organization(AppUser u) {
        if (u.getTenantId() != null) {
            return tenants.findById(u.getTenantId()).map(t -> t.getName()).orElse("");
        }
        if (u.getSupplierId() != null) {
            return suppliers.findById(u.getSupplierId()).map(s -> s.getName()).orElse("");
        }
        return "Flowpanel";
    }

    private Me toMe(AppUser u) {
        NamedRef tenant = u.getTenantId() == null ? null
                : tenants.findById(u.getTenantId()).map(t -> new NamedRef(t.getId(), t.getName())).orElse(null);
        NamedRef supplier = u.getSupplierId() == null ? null
                : suppliers.findById(u.getSupplierId()).map(s -> new NamedRef(s.getId(), s.getName())).orElse(null);
        return new Me(u.getId(), u.getPersona(), u.getDisplayName(), u.getJobTitle(), u.getRole(), tenant, supplier);
    }

    static CurrentUser toCurrent(AppUser u) {
        return new CurrentUser(u.getId(), u.getDisplayName(), u.getRole(), u.getTenantId(), u.getSupplierId());
    }
}
