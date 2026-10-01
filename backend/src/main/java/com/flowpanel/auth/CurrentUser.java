package com.flowpanel.auth;

/** Identity of the caller, decoded from the session JWT. tenantId is set for buyers, supplierId for supplier users. */
public record CurrentUser(Long userId, String displayName, Role role, Long tenantId, Long supplierId) {

    public boolean isBuyer() {
        return role == Role.BUYER;
    }

    public boolean isSupplier() {
        return role == Role.SUPPLIER;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
