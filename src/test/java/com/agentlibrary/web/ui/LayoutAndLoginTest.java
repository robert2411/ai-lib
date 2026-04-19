package com.agentlibrary.web.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the base layout, login page, and static assets.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LayoutAndLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPage_rendersWithoutAuth() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/login\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"username\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"password\"")));
    }

    @Test
    void loginPage_showsErrorParam() throws Exception {
        mockMvc.perform(get("/login?error"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username or password")));
    }

    @Test
    void loginPage_showsLogoutParam() throws Exception {
        mockMvc.perform(get("/login?logout"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("logged out")));
    }

    @Test
    void loginPage_includesLayoutElements() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("htmx.min.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("app.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-boost=\"true\"")));
    }

    @Test
    void staticCss_accessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk());
    }

    @Test
    void staticJs_accessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/js/htmx.min.js"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUser_seesNavBar() throws Exception {
        mockMvc.perform(get("/login").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AI Library")));
    }
}
