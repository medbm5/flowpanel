package com.flowpanel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import com.flowpanel.audit.AuditEventRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AuthIT extends AbstractIntegrationTest {

    @Autowired
    AuditEventRepository auditEvents;

    @Test
    void personasArePublic() throws Exception {
        JsonNode personas = call(get("/auth/personas"), status().isOk());
        assertThat(personas).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void demoLoginIssuesHttpOnlyCookieAndMeReturnsScope() throws Exception {
        Cookie cookie = login("claire");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();

        JsonNode me = call(get("/auth/me").cookie(cookie), status().isOk());
        assertThat(me.get("displayName").asText()).isEqualTo("Claire Dubois");
        assertThat(me.get("role").asText()).isEqualTo("BUYER");
        assertThat(me.get("tenant").get("name").asText()).isEqualTo("LogiNord");
        assertThat(me.get("supplier").isNull()).isTrue();

        JsonNode supplierMe = call(get("/auth/me").cookie(login("nadia")), status().isOk());
        assertThat(supplierMe.get("role").asText()).isEqualTo("SUPPLIER");
        assertThat(supplierMe.get("supplier").get("name").asText()).isEqualTo("InterSud Intérim");
    }

    @Test
    void loginIsAudited() throws Exception {
        long before = auditEvents.findByActionOrderByIdDesc("auth.login").size();
        login("marc");
        assertThat(auditEvents.findByActionOrderByIdDesc("auth.login")).hasSize((int) before + 1);
    }

    @Test
    void unknownPersonaIsRejected() throws Exception {
        mvc.perform(post("/auth/demo-login").contentType(MediaType.APPLICATION_JSON).content("{\"persona\":\"mallory\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tamperedTokenIsUnauthenticated() throws Exception {
        Cookie cookie = login("claire");
        Cookie forged = new Cookie("fp_session", cookie.getValue().substring(0, cookie.getValue().length() - 4) + "abcd");
        mvc.perform(get("/auth/me").cookie(forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookie() throws Exception {
        var response = mvc.perform(post("/auth/logout")).andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(response.getHeader("Set-Cookie")).contains("fp_session=").contains("Max-Age=0");
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        mvc.perform(post("/admin/demo/reset").cookie(login("claire"))).andExpect(status().isForbidden());
    }
}
