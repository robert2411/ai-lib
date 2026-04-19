package com.agentlibrary.web.api;

import com.agentlibrary.index.FacetService;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Harness;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for LookupApiController.
 */
@WebMvcTest(LookupApiController.class)
class LookupApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FacetService facetService;

    @MockBean
    private Bucket loginRateLimitBucket;

    @Test
    @WithMockUser
    void getTags_returnsJsonArray() throws Exception {
        when(facetService.getDistinctTags()).thenReturn(List.of("git", "vcs", "workflow"));

        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").value("git"))
                .andExpect(jsonPath("$[1]").value("vcs"))
                .andExpect(jsonPath("$[2]").value("workflow"));
    }

    @Test
    @WithMockUser
    void getTypes_returnsTypeSlugs() throws Exception {
        when(facetService.getDistinctTypes())
                .thenReturn(List.of(ArtifactType.SKILL, ArtifactType.PROMPT));

        mockMvc.perform(get("/api/v1/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("skill"))
                .andExpect(jsonPath("$[1]").value("prompt"));
    }

    @Test
    @WithMockUser
    void getHarnesses_returnsHarnessSlugs() throws Exception {
        when(facetService.getDistinctHarnesses())
                .thenReturn(List.of(Harness.CLAUDE, Harness.COPILOT));

        mockMvc.perform(get("/api/v1/harnesses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("claude"))
                .andExpect(jsonPath("$[1]").value("copilot"));
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isUnauthorized());
    }
}
