package com.agentlibrary.web.api;

import com.agentlibrary.install.BundleService;
import com.agentlibrary.install.InstallEntry;
import com.agentlibrary.install.InstallManifest;
import com.agentlibrary.install.InstallManifestResolver;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Harness;
import com.agentlibrary.service.NotFoundException;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for InstallApiController endpoints.
 */
@WebMvcTest(InstallApiController.class)
class InstallApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstallManifestResolver manifestResolver;

    @MockBean
    private BundleService bundleService;

    @MockBean
    private Bucket loginRateLimitBucket;

    // === AC#1: GET manifest returns correct install paths ===

    @Test
    @WithMockUser(roles = "USER")
    void manifest_returns200WithCorrectJson() throws Exception {
        InstallManifest manifest = new InstallManifest(
                List.of(new InstallEntry("git-helper", ArtifactType.SKILL, "skill.md", "~/.claude/skills/git-helper/")),
                Harness.CLAUDE
        );
        when(manifestResolver.resolve(eq(List.of("git-helper")), eq(Harness.CLAUDE)))
                .thenReturn(manifest);

        mockMvc.perform(get("/api/v1/install/manifest")
                        .param("slugs", "git-helper")
                        .param("harness", "claude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries[0].slug").value("git-helper"))
                .andExpect(jsonPath("$.entries[0].type").value("SKILL"))
                .andExpect(jsonPath("$.entries[0].sourcePath").value("skill.md"))
                .andExpect(jsonPath("$.entries[0].targetPath").value("~/.claude/skills/git-helper/"))
                .andExpect(jsonPath("$.harness").value("CLAUDE"));
    }

    // === AC#3: Unknown slug returns 404 ===

    @Test
    @WithMockUser(roles = "USER")
    void manifest_unknownSlug_returns404() throws Exception {
        when(manifestResolver.resolve(eq(List.of("unknown")), eq(Harness.CLAUDE)))
                .thenThrow(new NotFoundException("Artifact not found: unknown@latest"));

        mockMvc.perform(get("/api/v1/install/manifest")
                        .param("slugs", "unknown")
                        .param("harness", "claude"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void manifest_unknownHarness_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/install/manifest")
                        .param("slugs", "git-helper")
                        .param("harness", "badharness"))
                .andExpect(status().isBadRequest());
    }

    // === AC#2: GET bundle returns zip with Content-Disposition ===

    @Test
    @WithMockUser(roles = "USER")
    void bundle_returns200WithZipAndContentDisposition() throws Exception {
        byte[] fakeZip = new byte[]{0x50, 0x4B, 0x03, 0x04}; // zip magic bytes
        when(bundleService.bundleAsZip(eq(List.of("git-helper"))))
                .thenReturn(fakeZip);

        mockMvc.perform(get("/api/v1/install/bundle")
                        .param("slugs", "git-helper"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"install-bundle.zip\""))
                .andExpect(content().contentType("application/zip"));
    }

    // === AC#3: Unknown slug in bundle returns 404 ===

    @Test
    @WithMockUser(roles = "USER")
    void bundle_unknownSlug_returns404() throws Exception {
        when(bundleService.bundleAsZip(eq(List.of("unknown"))))
                .thenThrow(new NotFoundException("Unknown artifact: unknown"));

        mockMvc.perform(get("/api/v1/install/bundle")
                        .param("slugs", "unknown"))
                .andExpect(status().isNotFound());
    }

    // === Auth: unauthenticated returns 401 ===

    @Test
    void manifest_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/install/manifest")
                        .param("slugs", "git-helper")
                        .param("harness", "claude"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bundle_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/install/bundle")
                        .param("slugs", "git-helper"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void manifest_multipleSlugs_passesCorrectly() throws Exception {
        InstallManifest manifest = new InstallManifest(
                List.of(
                        new InstallEntry("git-helper", ArtifactType.SKILL, "skill.md", "~/.claude/skills/git-helper/"),
                        new InstallEntry("my-agent", ArtifactType.AGENT_CLAUDE, "agent.md", ".claude/agents/my-agent.md")
                ),
                Harness.CLAUDE
        );
        when(manifestResolver.resolve(eq(List.of("git-helper", "my-agent")), eq(Harness.CLAUDE)))
                .thenReturn(manifest);

        mockMvc.perform(get("/api/v1/install/manifest")
                        .param("slugs", "git-helper", "my-agent")
                        .param("harness", "claude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].slug").value("git-helper"))
                .andExpect(jsonPath("$.entries[1].slug").value("my-agent"));
    }
}
