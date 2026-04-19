package com.agentlibrary.web.ui;

import com.agentlibrary.auth.UsersFile;
import com.agentlibrary.auth.UsersFile.UserEntry;
import com.agentlibrary.index.IndexService;
import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.model.ArtifactType;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for AdminController.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IndexService indexService;

    @MockBean
    private UsersFile usersFile;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAdmin_returns200_withUsersTable() throws Exception {
        when(usersFile.loadAll()).thenReturn(List.of(
                new UserEntry("admin", "$2a$10$hash", List.of("ADMIN", "EDITOR")),
                new UserEntry("reader", "$2a$10$hash", List.of("USER"))
        ));
        when(indexService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(content().string(containsString("admin")))
                .andExpect(content().string(containsString("reader")))
                .andExpect(content().string(containsString("ADMIN")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAdmin_showsIndexCount() throws Exception {
        when(usersFile.loadAll()).thenReturn(List.of(
                new UserEntry("admin", "$2a$10$hash", List.of("ADMIN"))
        ));
        ArtifactMetadata meta = new ArtifactMetadata(
                "s1", "S1", ArtifactType.SKILL, "1.0.0",
                "desc", List.of(), List.of(), null, null, null, null,
                Instant.now(), Instant.now(), null, null, null
        );
        when(indexService.getAll()).thenReturn(List.of(meta, meta, meta));

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAdmin_nonAdmin_redirectsToHome() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void getAdmin_editor_redirectsToHome() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void postReindex_htmx_returnsToast() throws Exception {
        when(indexService.getAll()).thenReturn(List.of());
        doNothing().when(indexService).refresh();

        mockMvc.perform(post("/admin/reindex").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Index rebuilt successfully")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void postReindex_noHtmx_redirects() throws Exception {
        when(indexService.getAll()).thenReturn(List.of());
        doNothing().when(indexService).refresh();

        mockMvc.perform(post("/admin/reindex"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void postReindex_nonAdmin_redirectsToHome() throws Exception {
        mockMvc.perform(post("/admin/reindex"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void postReindex_onError_showsErrorToast() throws Exception {
        doThrow(new RuntimeException("disk full")).when(indexService).refresh();

        mockMvc.perform(post("/admin/reindex").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reindex failed")))
                .andExpect(content().string(containsString("disk full")));
    }
}
