package com.flowpanel.sourcing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "worker")
public class Worker {

    @Id
    private Long id;
    private Long supplierId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String[] skills;
    private String[] certifications;
    private int experienceYears;
    private LocalDate availableFrom;
    private LocalDate availableTo;
    private String profile;

    protected Worker() {
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    /** Text embedded for matching (no names: profiles are matched on skills, not identity). */
    public String matchingText() {
        return profile + " Compétences : " + String.join(", ", getSkills()) + ". Certifications : "
                + String.join(", ", getCertifications()) + ".";
    }

    public Long getId() {
        return id;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getCity() {
        return city;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public List<String> getSkills() {
        return skills == null ? List.of() : List.of(skills);
    }

    public List<String> getCertifications() {
        return certifications == null ? List.of() : List.of(certifications);
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public LocalDate getAvailableTo() {
        return availableTo;
    }

    public String getProfile() {
        return profile;
    }
}
