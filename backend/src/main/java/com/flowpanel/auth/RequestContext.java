package com.flowpanel.auth;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Access to the caller's tenant / supplier scope. Services use it to scope every query;
 * the UI is never trusted for scoping.
 */
@Component
public class RequestContext {

    private static final ThreadLocal<CurrentUser> OVERRIDE = new ThreadLocal<>();

    public Optional<CurrentUser> currentOptional() {
        CurrentUser override = OVERRIDE.get();
        if (override != null) {
            return Optional.of(override);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public CurrentUser current() {
        return currentOptional().orElseThrow(() -> new AccessDeniedException("Not authenticated"));
    }

    /** Tenant of a buyer. Throws 403 for supplier and admin users. */
    public Long requireTenantId() {
        CurrentUser user = current();
        if (!user.isBuyer()) {
            throw new AccessDeniedException("Buyer role required");
        }
        return user.tenantId();
    }

    /** Supplier of a supplier user. Throws 403 otherwise. */
    public Long requireSupplierId() {
        CurrentUser user = current();
        if (!user.isSupplier()) {
            throw new AccessDeniedException("Supplier role required");
        }
        return user.supplierId();
    }

    public void requireAdmin() {
        if (!current().isAdmin()) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    /** Runs code as another identity (used by the demo seeder, which has no HTTP request). */
    public <T> T runAs(CurrentUser user, Supplier<T> action) {
        CurrentUser previous = OVERRIDE.get();
        OVERRIDE.set(user);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                OVERRIDE.remove();
            } else {
                OVERRIDE.set(previous);
            }
        }
    }

    public void runAs(CurrentUser user, Runnable action) {
        runAs(user, () -> {
            action.run();
            return null;
        });
    }
}
