package com.flowpanel.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts the {@code postgres://user:password@host:port/db?sslmode=require} connection strings given by Render and Neon
 * in {@code DATABASE_URL}, and turns them into Spring's JDBC URL, username and password. A {@code jdbc:} URL is left as is.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("DATABASE_URL");
        Map<String, Object> converted = convert(url);
        if (converted.isEmpty()) {
            return;
        }
        if (environment.getProperty("DATABASE_USERNAME") != null) {
            converted.remove("spring.datasource.username");
        }
        if (environment.getProperty("DATABASE_PASSWORD") != null) {
            converted.remove("spring.datasource.password");
        }
        environment.getPropertySources().addFirst(new MapPropertySource("flowpanelDatabaseUrl", converted));
    }

    static Map<String, Object> convert(String rawUrl) {
        Map<String, Object> props = new HashMap<>();
        String url = clean(rawUrl);
        if (url == null) {
            return props;
        }
        if (url.startsWith("jdbc:")) {
            // Already a JDBC URL; only normalize it if pasting added whitespace or quotes.
            if (!url.equals(rawUrl)) {
                props.put("spring.datasource.url", url);
            }
            return props;
        }
        if (!(url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
            throw new IllegalStateException("DATABASE_URL must start with postgres://, postgresql:// or jdbc:postgresql:// "
                    + "(got a value starting with '" + url.substring(0, Math.min(12, url.length())) + "…')");
        }
        URI uri = URI.create(url);
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath());
        if (uri.getRawQuery() != null) {
            jdbc.append('?').append(uri.getRawQuery());
        }
        props.put("spring.datasource.url", jdbc.toString());
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
            props.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
            if (colon >= 0) {
                props.put("spring.datasource.password", URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8));
            }
        }
        return props;
    }

    /** Removes what copy-pasting often adds: surrounding whitespace or quotes, a psql command, or a "DATABASE_URL=" prefix. */
    static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.startsWith("psql ")) {
            v = v.substring(5).strip();
        }
        if (v.startsWith("DATABASE_URL=")) {
            v = v.substring("DATABASE_URL=".length()).strip();
        }
        while (v.length() >= 2 && (v.startsWith("\"") || v.startsWith("'")) && v.charAt(v.length() - 1) == v.charAt(0)) {
            v = v.substring(1, v.length() - 1).strip();
        }
        return v;
    }
}
