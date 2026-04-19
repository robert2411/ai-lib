package com.agentlibrary.web.api;

import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.NotFoundException;
import com.agentlibrary.service.ResolvedGroupMember;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ArtifactApiController GET endpoints.
 */
@WebMvcTest(ArtifactApiController.class)
class ArtifactApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtifactService artifactService;

    @MockBean
    private Bucket loginRateLimitBucket;

    private ArtifactMetadata sampleMetadata() {
        return new ArtifactMetadata(
                "git-helper", "Git Helper", ArtifactType.SKILL, "1.0.0",
                "A git helper skill", List.of(Harness.CLAUDE), List.of("git", "vcs"),
                null, null, null, null,
                Instant.parse("2024-01-01T00:00:00Z"), null, null, null, null
        );
    }

    private Artifact sampleArtifact() {
        return new Artifact(sampleMetadata(), "Body content here.");
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_returns200WithPaginatedJson() throws Exception {
        when(artifactService.list(any(), eq(0), eq(20)))
                .thenReturn(List.of(sampleMetadata()));
        when(artifactService.count(any())).thenReturn(1L);

        mockMvc.perform(get("/api/v1/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].name").value("git-helper"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_withSearchQuery_delegatesToSearch() throws Exception {
        when(artifactService.search(eq("git"), any()))
                .thenReturn(List.of(sampleMetadata()));

        mockMvc.perform(get("/api/v1/artifacts").param("q", "git"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("git-helper"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_withTypeFilter_filters() throws Exception {
        when(artifactService.list(any(), eq(0), eq(20)))
                .thenReturn(List.of(sampleMetadata()));
        when(artifactService.count(any())).thenReturn(1L);

        mockMvc.perform(get("/api/v1/artifacts")
                        .param("type", "skill")
                        .param("harness", "claude")
                        .param("tag", "git"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("skill"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void get_returnsFullArtifact() throws Exception {
        when(artifactService.get("git-helper", "1.0.0")).thenReturn(sampleArtifact());

        mockMvc.perform(get("/api/v1/artifacts/git-helper/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("git-helper"))
                .andExpect(jsonPath("$.content").value("Body content here."))
                .andExpect(jsonPath("$.version").value("1.0.0"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void get_unknownSlug_returns404() throws Exception {
        when(artifactService.get("unknown-slug", "1.0.0"))
                .thenThrow(new NotFoundException("Artifact not found: unknown-slug@1.0.0"));

        mockMvc.perform(get("/api/v1/artifacts/unknown-slug/1.0.0"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getLatest_returnsArtifact() throws Exception {
        when(artifactService.get("git-helper", "latest")).thenReturn(sampleArtifact());

        mockMvc.perform(get("/api/v1/artifacts/git-helper/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("git-helper"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getMembers_returnsOrderedList() throws Exception {
        ArtifactMetadata mgrMeta = new ArtifactMetadata(
                "mgr-agent", "Manager", ArtifactType.AGENT_CLAUDE, "1.0.0",
                "Manager agent", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null, null, null, null, null, null
        );
        ArtifactMetadata implMeta = new ArtifactMetadata(
                "impl-agent", "Impl", ArtifactType.AGENT_CLAUDE, "1.0.0",
                "Impl agent", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null, null, null, null, null, null
        );

        when(artifactService.resolveGroup("my-group")).thenReturn(List.of(
                new ResolvedGroupMember("mgr-agent", "manager", mgrMeta),
                new ResolvedGroupMember("impl-agent", "implementation", implMeta)
        ));

        mockMvc.perform(get("/api/v1/artifacts/my-group/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("mgr-agent"))
                .andExpect(jsonPath("$[0].role").value("manager"))
                .andExpect(jsonPath("$[1].slug").value("impl-agent"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getMembers_nonGroup_returns400() throws Exception {
        when(artifactService.resolveGroup("git-helper"))
                .thenThrow(new IllegalArgumentException("Artifact 'git-helper' is not an agent-group type"));

        mockMvc.perform(get("/api/v1/artifacts/git-helper/members"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/artifacts"))
                .andExpect(status().isUnauthorized());
    }
}
