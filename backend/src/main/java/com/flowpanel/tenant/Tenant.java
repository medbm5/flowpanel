package com.flowpanel.tenant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private Long id;
    private String code;
    private String name;
    private String sector;

    protected Tenant() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSector() {
        return sector;
    }
}
