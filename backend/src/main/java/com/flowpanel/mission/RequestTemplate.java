package com.flowpanel.mission;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "request_template")
public class RequestTemplate {

    @Id
    private String code;
    private String title;
    private String site;
    private String description;
    private String emailText;

    protected RequestTemplate() {
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getSite() {
        return site;
    }

    public String getDescription() {
        return description;
    }

    public String getEmailText() {
        return emailText;
    }
}
