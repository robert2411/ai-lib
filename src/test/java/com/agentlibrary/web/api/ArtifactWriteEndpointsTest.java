package com.agentlibrary.web.api;

import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.NotFoundException;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ArtifactApiController write endpoints (POST, PUT, DELETE).
 */
@WebMvcTest(ArtifactApiController.class)
@Import(ArtifactWriteEndpointsTest.TestSecurityConfig.class)
class ArtifactWriteEndpointsTest {

    /**
     * Test security config that mirrors production SecurityConfig authorization rules
     * without requiring file-based UserDetailsService.
     */
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .httpBasic(basic -> {})
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/healthz").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/v1/artifacts/**").hasAnyRole("EDITOR", "ADMIN")
                            .requestMatchers(HttpMethod.PUT, "/api/v1/artifacts/**").hasAnyRole("EDITOR", "ADMIN")
                            .requestMatchers(HttpMethod.DELETE, "/api/v1/artifacts/**").hasAnyRole("EDITOR", "ADMIN")
                            .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                            .anyRequest().authenticated()
                    );
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtifactService artifactService;

    @MockBean
    private Bucket loginRateLimitBucket;

    private static final String VALID_RAW_CONTENT = """
            ---
            name: git-helper
            type: skill
            version: 1.0.0
            description: A git helper skill
            harnesses:
              - claude
            tags:
              - git
            ---
            This is the body content.
            """;

    @Test
    @WithMockUser(roles = "EDITOR")
    void post_createsArtifact_returns201WithLocation() throws Exception {
        when(artifactService.createOrUpdate(anyString(), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "commit"));

        mockMvc.perform(post("/api/v1/artifacts")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_RAW_CONTENT)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/artifacts/git-helper/1.0.0"))
                .andExpect(jsonPath("$.name").value("git-helper"))
                .andExpect(jsonPath("$.version").value("1.0.0"));

        verify(artifactService).createOrUpdate(anyString(), any(User.class));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void post_upsert_existingSlugStillReturns201() throws Exception {
        // POST is UPSERT — no 409 conflict even if slug exists
        when(artifactService.createOrUpdate(anyString(), any(User.class)))
                .thenReturn(new CommitResult("def456", Instant.now(), "update"));

        mockMvc.perform(post("/api/v1/artifacts")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_RAW_CONTENT)
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void put_updatesArtifact_returns200() throws Exception {
        when(artifactService.createOrUpdate(anyString(), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "update"));

        mockMvc.perform(put("/api/v1/artifacts/git-helper/1.0.0")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_RAW_CONTENT)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("git-helper"));

        verify(artifactService).createOrUpdate(anyString(), any(User.class));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void put_slugMismatch_returns400() throws Exception {
        // Path says "other-slug" but content has "git-helper"
        mockMvc.perform(put("/api/v1/artifacts/other-slug/1.0.0")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_RAW_CONTENT)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_removesArtifact_returns204() throws Exception {
        doNothing().when(artifactService).delete(eq("git-helper"), any(User.class));

        mockMvc.perform(delete("/api/v1/artifacts/git-helper")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(artifactService).delete(eq("git-helper"), any(User.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_unknownSlug_returns404() throws Exception {
        doThrow(new NotFoundException("Unknown artifact: unknown"))
                .when(artifactService).delete(eq("unknown"), any(User.class));

        mockMvc.perform(delete("/api/v1/artifacts/unknown")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ===== Authentication tests (AC #4) =====

    @Test
    void post_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/artifacts")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_RAW_CONTENT)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void put_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/artifacts/git-helper/1.0.0")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_RAW_CONTENT)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/artifacts/git-helper")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void post_insufficientRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/artifacts")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_RAW_CONTENT)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void put_insufficientRole_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/artifacts/git-helper/1.0.0")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_RAW_CONTENT)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void delete_insufficientRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/artifacts/git-helper")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
