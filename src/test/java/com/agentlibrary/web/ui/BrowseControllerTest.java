package com.agentlibrary.web.ui;

import com.agentlibrary.index.FacetService;
import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for BrowseController.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BrowseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtifactService artifactService;

    @MockBean
    private FacetService facetService;

    private ArtifactMetadata sampleArtifact(String name, ArtifactType type) {
        return new ArtifactMetadata(
                name, name + " Title", type, "1.0.0",
                "A " + type.slug(), List.of(Harness.CLAUDE), List.of("test-tag"),
                "general", "java", "admin", null,
                Instant.now(), Instant.now(), null, null, null
        );
    }

    private void setupFacets() {
        when(facetService.getDistinctTypes()).thenReturn(List.of(ArtifactType.SKILL, ArtifactType.AGENT_GROUP));
        when(facetService.getDistinctHarnesses()).thenReturn(List.of(Harness.CLAUDE, Harness.COPILOT));
        when(facetService.getDistinctTags()).thenReturn(List.of("coding", "testing"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void browse_returns200_withFiltersAndResults() throws Exception {
        setupFacets();
        when(artifactService.list(any(), anyInt(), anyInt())).thenReturn(List.of(sampleArtifact("s1", ArtifactType.SKILL)));
        when(artifactService.count(any())).thenReturn(1L);

        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(view().name("browse"))
                .andExpect(content().string(containsString("Browse Artifacts")))
                .andExpect(content().string(containsString("s1 Title")))
                .andExpect(content().string(containsString("All Types")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void browse_withTypeFilter_passesFilter() throws Exception {
        setupFacets();
        when(artifactService.list(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(artifactService.count(any())).thenReturn(0L);

        mockMvc.perform(get("/browse?type=skill"))
                .andExpect(status().isOk())
                .andExpect(view().name("browse"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void browseResults_withHxRequest_returnsFragment() throws Exception {
        when(artifactService.list(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(artifactService.count(any())).thenReturn(0L);

        mockMvc.perform(get("/browse/results").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No artifacts match your filters")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void browseResults_withoutHxRequest_redirects() throws Exception {
        mockMvc.perform(get("/browse/results"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/browse"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void browse_emptyResults_showsMessage() throws Exception {
        setupFacets();
        when(artifactService.list(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(artifactService.count(any())).thenReturn(0L);

        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No artifacts match your filters")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void browse_agentGroupType_isSelectable() throws Exception {
        setupFacets();
        when(artifactService.list(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(artifactService.count(any())).thenReturn(0L);

        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("agent-group")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void browse_agentGroupCard_showsMemberBadge() throws Exception {
        setupFacets();
        ArtifactMetadata group = new ArtifactMetadata(
                "my-group", "My Group", ArtifactType.AGENT_GROUP, "1.0.0",
                "A group of agents", List.of(), List.of(),
                null, null, null, null,
                Instant.now(), Instant.now(), null,
                List.of(new AgentGroupMember("a1", "manager"),
                        new AgentGroupMember("a2", "implementation"),
                        new AgentGroupMember("a3", "analyse")),
                null
        );
        when(artifactService.list(any(), anyInt(), anyInt())).thenReturn(List.of(group));
        when(artifactService.count(any())).thenReturn(1L);

        mockMvc.perform(get("/browse"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3 agents")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void browse_withSearchQuery_usesSearch() throws Exception {
        setupFacets();
        when(artifactService.search(anyString(), any())).thenReturn(
                List.of(sampleArtifact("found", ArtifactType.SKILL)));

        mockMvc.perform(get("/browse?q=test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("found Title")));
    }
}
