package com.flowpanel.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private Long id;
    private String persona;
    private String displayName;
    private String jobTitle;
    private String email;
    @Enumerated(EnumType.STRING)
    private Role role;
    private Long tenantId;
    private Long supplierId;

    protected AppUser() {
    }

    public Long getId() {
        return id;
    }

    public String getPersona() {
        return persona;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getSupplierId() {
        return supplierId;
    }
}
