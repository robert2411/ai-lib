package com.agentlibrary.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SecurityConfig.
 * Verifies context loads, bean wiring, and security rules.
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
        assertNotNull(context.getBean(RateLimitConfig.class));
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
}
