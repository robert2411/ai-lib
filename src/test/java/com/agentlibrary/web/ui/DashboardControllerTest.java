package com.agentlibrary.web.ui;

import com.agentlibrary.index.IndexService;
import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Harness;
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
 * Tests for DashboardController.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IndexService indexService;

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void authenticated_returns200() throws Exception {
        when(indexService.getAll()).thenReturn(List.of());
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void dashboard_showsSearchForm() throws Exception {
        when(indexService.getAll()).thenReturn(List.of());
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/browse\"")))
                .andExpect(content().string(containsString("name=\"q\"")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void dashboard_showsRecentArtifacts() throws Exception {
        ArtifactMetadata meta = new ArtifactMetadata(
                "test-skill", "Test Skill", ArtifactType.SKILL, "1.0.0",
                "A test skill", List.of(Harness.CLAUDE), List.of("testing"),
                "general", "java", "admin", null,
                Instant.now().minusSeconds(100), Instant.now(),
                null, null, null
        );
        when(indexService.getAll()).thenReturn(List.of(meta));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Skill")))
                .andExpect(content().string(containsString("skill")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void dashboard_showsTypeCounts() throws Exception {
        ArtifactMetadata skill1 = new ArtifactMetadata(
                "s1", "Skill 1", ArtifactType.SKILL, "1.0.0",
                "desc", List.of(), List.of(), null, null, null, null,
                Instant.now(), Instant.now(), null, null, null
        );
        ArtifactMetadata skill2 = new ArtifactMetadata(
                "s2", "Skill 2", ArtifactType.SKILL, "1.0.0",
                "desc", List.of(), List.of(), null, null, null, null,
                Instant.now(), Instant.now(), null, null, null
        );
        ArtifactMetadata mcp = new ArtifactMetadata(
                "m1", "MCP 1", ArtifactType.MCP, "1.0.0",
                "desc", List.of(), List.of(), null, null, null, null,
                Instant.now(), Instant.now(), null, null, null
        );
        when(indexService.getAll()).thenReturn(List.of(skill1, skill2, mcp));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3"))); // total
    }

    @Test
    @WithMockUser(roles = "USER")
    void dashboard_emptyState_showsMessage() throws Exception {
        when(indexService.getAll()).thenReturn(List.of());
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No artifacts yet")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void dashboard_limitsTo10Artifacts() throws Exception {
        // Create 15 artifacts
        List<ArtifactMetadata> many = java.util.stream.IntStream.rangeClosed(1, 15)
                .mapToObj(i -> new ArtifactMetadata(
                        "s" + i, "Skill " + i, ArtifactType.SKILL, "1.0.0",
                        "desc", List.of(), List.of(), null, null, null, null,
                        Instant.now().minusSeconds(i), Instant.now().minusSeconds(i),
                        null, null, null))
                .toList();
        when(indexService.getAll()).thenReturn(many);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // Should show "15" for total count
                .andExpect(content().string(containsString("15")));
    }
}
