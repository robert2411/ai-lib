package com.agentlibrary.config;

import com.agentlibrary.auth.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link RateLimitFilter}.
 * Verifies per-IP rate limiting on POST /login.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void resetRateLimits() {
        // Reset buckets between tests to ensure isolation
        loginRateLimiter.resetBucket("127.0.0.1");
        loginRateLimiter.resetBucket("10.0.0.1");
        loginRateLimiter.resetBucket("10.0.0.2");
    }

    @Test
    void tenPostsToLogin_allPass_notRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/login")
                            .param("username", "wrong")
                            .param("password", "wrong"))
                    .andExpect(result -> {
                        int statusCode = result.getResponse().getStatus();
                        // Should be 302 (redirect to /login?error) or 401, but NOT 429
                        org.junit.jupiter.api.Assertions.assertNotEquals(429, statusCode,
                                "Request " + " should not be rate-limited");
                    });
        }
    }

    @Test
    void eleventhPost_returns429() throws Exception {
        // Exhaust 10 tokens
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/login")
                    .param("username", "wrong")
                    .param("password", "wrong"));
        }
        // 11th should be 429
        mockMvc.perform(post("/login")
                        .param("username", "wrong")
                        .param("password", "wrong"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void differentIps_independentRateLimits() throws Exception {
        // Exhaust one IP's bucket via X-Forwarded-For
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/login")
                    .header("X-Forwarded-For", "10.0.0.1")
                    .param("username", "wrong")
                    .param("password", "wrong"));
        }

        // That IP should be rate-limited
        mockMvc.perform(post("/login")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .param("username", "wrong")
                        .param("password", "wrong"))
                .andExpect(status().isTooManyRequests());

        // A different IP should not be affected
        mockMvc.perform(post("/login")
                        .header("X-Forwarded-For", "10.0.0.2")
                        .param("username", "wrong")
                        .param("password", "wrong"))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(429, statusCode,
                            "Different IP should not be rate-limited");
                });
    }
}
