package com.flowpanel.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Person names that must never reach an LLM provider: workers, platform users and known contacts.
 * Masking a superset of the caller's tenant is deliberate (safe by default).
 */
@Component
public class PiiDirectory {

    private final JdbcTemplate jdbc;

    public PiiDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> knownNames() {
        List<String> names = new ArrayList<>();
        names.addAll(jdbc.queryForList("select first_name || ' ' || last_name from worker", String.class));
        names.addAll(jdbc.queryForList("select display_name from app_user", String.class));
        names.addAll(jdbc.queryForList("select full_name from known_contact", String.class));
        return names;
    }
}
