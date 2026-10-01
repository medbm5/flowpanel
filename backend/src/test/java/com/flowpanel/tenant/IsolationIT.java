package com.flowpanel.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowpanel.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

/** Tenant and supplier boundaries. Out-of-scope resources are 404, never 403, so existence is not leaked. */
class IsolationIT extends AbstractIntegrationTest {

    static final long LOGINORD = 1, METALPRO = 2;
    static final long INTERSUD = 1, PROXI = 2;

    @Test
    void buyerReadsOwnTenantButNotAnotherTenant() throws Exception {
        Cookie claire = login("claire");
        call(get("/tenants/" + LOGINORD).cookie(claire), status().isOk());
        JsonNode problem = call(get("/tenants/" + METALPRO).cookie(claire), status().isNotFound());
        assertThat(problem.get("title").asText()).isEqualTo("Not found");

        Cookie marc = login("marc");
        call(get("/tenants/" + LOGINORD).cookie(marc), status().isNotFound());
    }

    @Test
    void notFoundForOtherTenantLooksLikeNonExistentId() throws Exception {
        Cookie claire = login("claire");
        JsonNode other = call(get("/tenants/" + METALPRO).cookie(claire), status().isNotFound());
        JsonNode missing = call(get("/tenants/9999").cookie(claire), status().isNotFound());
        assertThat(other.get("status")).isEqualTo(missing.get("status"));
    }

    @Test
    void buyerSeesOnlyPanelSuppliers() throws Exception {
        Cookie marc = login("marc");
        JsonNode panel = call(get("/suppliers").cookie(marc), status().isOk());
        assertThat(panel.findValuesAsText("code")).containsExactlyInAnyOrder("proxi", "atlas");
        call(get("/suppliers/" + INTERSUD).cookie(marc), status().isNotFound());
    }

    @Test
    void supplierUserCannotReadAnotherSupplier() throws Exception {
        Cookie nadia = login("nadia");
        call(get("/suppliers/" + INTERSUD).cookie(nadia), status().isOk());
        call(get("/suppliers/" + PROXI).cookie(nadia), status().isNotFound());
        JsonNode list = call(get("/suppliers").cookie(nadia), status().isOk());
        assertThat(list.findValuesAsText("code")).containsExactly("intersud");
    }

    @Test
    void supplierSeesOnlyTenantsItServes() throws Exception {
        Cookie nadia = login("nadia");
        call(get("/tenants/" + LOGINORD).cookie(nadia), status().isOk());
        call(get("/tenants/" + METALPRO).cookie(nadia), status().isNotFound());
    }
}
