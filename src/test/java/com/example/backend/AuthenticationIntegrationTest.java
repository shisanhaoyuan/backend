package com.example.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anonymousHelloReturns401() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void anonymousClientCanObtainCsrfToken() throws Exception {
        mvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(unauthenticated());
    }

    @Test
    void successfulLoginRotatesSessionAndAllowsHello() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String originalSessionId = session.getId();

        login(session);

        assertThat(session.getId()).isNotEqualTo(originalSessionId);
        mvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Hello World"));
        // Authentication belongs to this session, not to all clients.
        mvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource(value = {"test,wrong", "unknown,123456", "test,''"})
    void invalidCredentialsReturn401(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/login").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username).param("password", password))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"))
                .andExpect(unauthenticated());
        mvc.perform(get("/api/hello").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenReturns403() throws Exception {
        mvc.perform(post("/api/login")
                        .param("username", "test").param("password", "123456"))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void loginWithInvalidCsrfTokenReturns403() throws Exception {
        MockHttpSession session = new MockHttpSession();
        csrfToken(session);
        mvc.perform(post("/api/login").session(session)
                        .header("X-CSRF-TOKEN", "invalid")
                        .param("username", "test").param("password", "123456"))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void logoutInvalidatesAuthenticatedSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session);

        // Successful authentication clears the pre-login CSRF token.
        mvc.perform(post("/api/logout").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCsrfDoesNotEndSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session);
        mvc.perform(post("/api/logout").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk());
    }

    private void login(MockHttpSession session) throws Exception {
        mvc.perform(post("/api/login").session(session)
                        .header("X-CSRF-TOKEN", csrfToken(session))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test").param("password", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(authenticated().withUsername("test"));
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
