package com.agentlibrary.web.ui;

import com.agentlibrary.metadata.ValidationException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for EditController — artifact creation, editing, and deletion.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtifactService artifactService;

    private Artifact sampleArtifact() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-skill", "My Skill", ArtifactType.SKILL, "1.0.0",
                "A useful skill", List.of(Harness.CLAUDE), List.of("coding"),
                "general", "java", "admin", Visibility.TEAM,
                Instant.now(), Instant.now(), null, null, null
        );
        return new Artifact(meta, "Skill content body");
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void getNew_returns200WithEmptyForm() throws Exception {
        mockMvc.perform(get("/artifacts/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("edit"))
                .andExpect(model().attribute("isNew", true))
                .andExpect(content().string(containsString("New Artifact")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getNew_adminCanAccess() throws Exception {
        mockMvc.perform(get("/artifacts/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("edit"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getNew_userGetsForbidden() throws Exception {
        mockMvc.perform(get("/artifacts/new"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void getEdit_prefillsForm() throws Exception {
        when(artifactService.get("my-skill", "latest")).thenReturn(sampleArtifact());

        mockMvc.perform(get("/artifacts/my-skill/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("edit"))
                .andExpect(model().attribute("isNew", false))
                .andExpect(content().string(containsString("my-skill")))
                .andExpect(content().string(containsString("My Skill")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEdit_adminCanAccess() throws Exception {
        when(artifactService.get("my-skill", "latest")).thenReturn(sampleArtifact());

        mockMvc.perform(get("/artifacts/my-skill/edit"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void postSave_validData_savesAndRedirects() throws Exception {
        when(artifactService.createOrUpdate(anyString(), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "created"));

        mockMvc.perform(post("/artifacts/save")
                        .param("name", "new-skill")
                        .param("title", "New Skill")
                        .param("type", "skill")
                        .param("version", "1.0.0")
                        .param("description", "A new skill")
                        .param("content", "Body content"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/artifacts/new-skill/1.0.0"));

        verify(artifactService).createOrUpdate(anyString(), any(User.class));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void postSave_blankName_returnsFormWithErrors() throws Exception {
        mockMvc.perform(post("/artifacts/save")
                        .param("name", "")
                        .param("type", "skill")
                        .param("version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(view().name("edit"))
                .andExpect(content().string(containsString("Name is required")));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void postSave_validationException_returnsFormWithError() throws Exception {
        when(artifactService.createOrUpdate(anyString(), any(User.class)))
                .thenThrow(new ValidationException(List.of("Invalid metadata field")));

        mockMvc.perform(post("/artifacts/save")
                        .param("name", "bad-skill")
                        .param("title", "Bad Skill")
                        .param("type", "skill")
                        .param("version", "1.0.0")
                        .param("content", "body"))
                .andExpect(status().isOk())
                .andExpect(view().name("edit"))
                .andExpect(content().string(containsString("Invalid metadata field")));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void postDelete_deletesAndRedirects() throws Exception {
        doNothing().when(artifactService).delete(anyString(), any(User.class));

        mockMvc.perform(post("/artifacts/my-skill/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/browse"));

        verify(artifactService).delete(eq("my-skill"), any(User.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void postSave_userGetsForbidden() throws Exception {
        mockMvc.perform(post("/artifacts/save")
                        .param("name", "test")
                        .param("type", "skill")
                        .param("version", "1.0.0"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void postDelete_userGetsForbidden() throws Exception {
        mockMvc.perform(post("/artifacts/my-skill/delete"))
                .andExpect(status().isForbidden());
    }
}
