package com.agentlibrary.web.api;

import com.agentlibrary.index.IndexService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AdminApiController.
 */
@WebMvcTest(AdminApiController.class)
@Import(AdminApiControllerTest.TestSecurityConfig.class)
class AdminApiControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .httpBasic(basic -> {})
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated()
                    );
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IndexService indexService;

    @MockBean
    private Bucket loginRateLimitBucket;

    @Test
    @WithMockUser(roles = "ADMIN")
    void reindex_withAdminRole_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reindex").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.message").value("Index rebuilt successfully"));

        verify(indexService).refresh();
    }

    @Test
    @WithMockUser(roles = "USER")
    void reindex_withUserRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reindex").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void reindex_withEditorRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reindex").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reindex_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reindex").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
