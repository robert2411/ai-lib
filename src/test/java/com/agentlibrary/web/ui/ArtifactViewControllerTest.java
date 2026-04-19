package com.agentlibrary.web.ui;

import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.NotFoundException;
import com.agentlibrary.service.ResolvedGroupMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for ArtifactViewController and WebExceptionHandler.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ArtifactViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtifactService artifactService;

    private Artifact sampleArtifact() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-skill", "My Skill", ArtifactType.SKILL, "1.2.0",
                "A useful skill", List.of(Harness.CLAUDE), List.of("coding"),
                "general", "java", "admin", null,
                Instant.now(), Instant.now(), null, null, null
        );
        return new Artifact(meta, "---\nname: my-skill\n---\nContent body here");
    }

    private Artifact groupArtifact() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-group", "My Group", ArtifactType.AGENT_GROUP, "1.0.0",
                "A group of agents", List.of(), List.of(),
                null, null, null, null,
                Instant.now(), Instant.now(), null,
                List.of(new AgentGroupMember("agent-a", "manager"),
                        new AgentGroupMember("agent-b", "implementation")),
                null
        );
        return new Artifact(meta, "---\nname: my-group\n---\nGroup content");
    }

    @Test
    @WithMockUser(roles = "USER")
    void getArtifactSlug_redirectsToLatest() throws Exception {
        when(artifactService.get("my-skill", "latest")).thenReturn(sampleArtifact());

        mockMvc.perform(get("/artifacts/my-skill"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/artifacts/my-skill/1.2.0"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getArtifactDetail_returns200() throws Exception {
        when(artifactService.get("my-skill", "1.2.0")).thenReturn(sampleArtifact());
        when(artifactService.getVersions("my-skill")).thenReturn(List.of(
                new VersionRef("1.2.0", "abc123", Instant.now()),
                new VersionRef("1.1.0", "def456", Instant.now().minusSeconds(1000))
        ));

        mockMvc.perform(get("/artifacts/my-skill/1.2.0"))
                .andExpect(status().isOk())
                .andExpect(view().name("artifact"))
                .andExpect(content().string(containsString("My Skill")))
                .andExpect(content().string(containsString("1.2.0")))
                .andExpect(content().string(containsString("1.1.0")))
                .andExpect(content().string(containsString("Content body here")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getArtifactDetail_showsInstallCommand() throws Exception {
        when(artifactService.get("my-skill", "1.2.0")).thenReturn(sampleArtifact());
        when(artifactService.getVersions("my-skill")).thenReturn(List.of(
                new VersionRef("1.2.0", "abc123", Instant.now())
        ));

        mockMvc.perform(get("/artifacts/my-skill/1.2.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("agent-lib install skill/my-skill@1.2.0")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getArtifactDetail_showsRawDownloadLink() throws Exception {
        when(artifactService.get("my-skill", "1.2.0")).thenReturn(sampleArtifact());
        when(artifactService.getVersions("my-skill")).thenReturn(List.of(
                new VersionRef("1.2.0", "abc123", Instant.now())
        ));

        mockMvc.perform(get("/artifacts/my-skill/1.2.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/artifacts/my-skill/1.2.0/raw")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getArtifactRaw_returnsPlainText() throws Exception {
        when(artifactService.get("my-skill", "1.2.0")).thenReturn(sampleArtifact());

        mockMvc.perform(get("/artifacts/my-skill/1.2.0/raw"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("Content body here")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getArtifactContent_returnsFragment() throws Exception {
        when(artifactService.get("my-skill", "1.2.0")).thenReturn(sampleArtifact());

        mockMvc.perform(get("/artifacts/my-skill/1.2.0/content"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Content body here")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getArtifactNotFound_returns404() throws Exception {
        when(artifactService.get("nonexistent", "latest"))
                .thenThrow(new NotFoundException("Artifact not found: nonexistent@latest"));

        mockMvc.perform(get("/artifacts/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Not Found")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void agentGroupDetail_showsMembersPanel() throws Exception {
        Artifact group = groupArtifact();
        when(artifactService.get("my-group", "1.0.0")).thenReturn(group);
        when(artifactService.getVersions("my-group")).thenReturn(List.of(
                new VersionRef("1.0.0", "abc123", Instant.now())
        ));

        ArtifactMetadata memberMeta = new ArtifactMetadata(
                "agent-a", "Agent A", ArtifactType.AGENT_CLAUDE, "1.0.0",
                "desc", List.of(), List.of(), null, null, null, null,
                Instant.now(), Instant.now(), null, null, null
        );
        when(artifactService.resolveGroup("my-group")).thenReturn(List.of(
                new ResolvedGroupMember("agent-a", "manager", memberMeta),
                new ResolvedGroupMember("agent-b", "implementation", memberMeta)
        ));

        mockMvc.perform(get("/artifacts/my-group/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Group Members")))
                .andExpect(content().string(containsString("agent-a")))
                .andExpect(content().string(containsString("manager")))
                .andExpect(content().string(containsString("/artifacts/agent-a")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void agentGroupDetail_showsInstallGroupButton() throws Exception {
        Artifact group = groupArtifact();
        when(artifactService.get("my-group", "1.0.0")).thenReturn(group);
        when(artifactService.getVersions("my-group")).thenReturn(List.of(
                new VersionRef("1.0.0", "abc123", Instant.now())
        ));

        ArtifactMetadata memberMeta = new ArtifactMetadata(
                "agent-a", "Agent A", ArtifactType.AGENT_CLAUDE, "1.0.0",
                "desc", List.of(), List.of(), null, null, null, null,
                Instant.now(), Instant.now(), null, null, null
        );
        when(artifactService.resolveGroup("my-group")).thenReturn(List.of(
                new ResolvedGroupMember("agent-a", "manager", memberMeta)
        ));

        mockMvc.perform(get("/artifacts/my-group/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Install Group")))
                .andExpect(content().string(containsString("agent-lib install agent-group/my-group")));
    }
}
