package com.flowpanel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every integration test: one shared pgvector Postgres container for the whole JVM
 * (singleton pattern, so the cached Spring context never points at a stopped container).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("flowpanel")
            .withUsername("flowpanel")
            .withPassword("flowpanel");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** Logs in as a demo persona and returns the session cookie. */
    protected Cookie login(String persona) throws Exception {
        MvcResult result = mvc.perform(post("/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":\"" + persona + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("fp_session");
    }

    /** Performs a request, asserts the status, and parses the JSON body (null when empty). */
    protected JsonNode call(RequestBuilder request, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(expectedStatus).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }
}
