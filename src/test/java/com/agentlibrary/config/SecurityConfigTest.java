package com.agentlibrary.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SecurityConfig.
 * Verifies context loads, bean wiring, authentication, and role-based access.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads_allBeansWired() {
        assertNotNull(context.getBean(UserDetailsService.class));
        assertNotNull(context.getBean(PasswordEncoder.class));
        assertNotNull(context.getBean(SecurityConfig.class));
        assertNotNull(context.getBean(RoleHierarchy.class));
    }

    @Test
    void unauthenticated_getApiEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/artifacts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isNotFound()); // 404 because no controller, but not 401
    }

    @Test
    void actuatorHealthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void validCredentials_authenticateSuccessfully() throws Exception {
        // admin user from data/users.yaml
        mockMvc.perform(get("/api/v1/artifacts")
                        .with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCredentials_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/artifacts")
                        .with(httpBasic("admin", "wrongpassword")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRole_canAccessAdminEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reindex")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void userRole_cannotAccessWriteEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/artifacts")
                        .contentType("text/plain")
                        .content("---\nname: test\n---\nbody")
                        .with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userRole_cannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reindex")
                        .with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void editorRole_canAccessWriteEndpoints() throws Exception {
        // Editor can POST to artifacts — we only test auth passes (endpoint may error on content, but not 403)
        mockMvc.perform(put("/api/v1/artifacts/test/1.0.0")
                        .contentType("text/plain")
                        .content("---\nname: test\nversion: 1.0.0\n---\nbody")
                        .with(user("editor").roles("EDITOR")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Not forbidden — auth passed. Could be 200, 201, 400, 404, 500 depending on service
                    assertNotEquals(403, status, "EDITOR should not be forbidden from write endpoints");
                });
    }
}
