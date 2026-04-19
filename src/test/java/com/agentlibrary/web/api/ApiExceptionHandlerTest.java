package com.agentlibrary.web.api;

import com.agentlibrary.metadata.ValidationException;
import com.agentlibrary.service.NotFoundException;
import com.agentlibrary.storage.StorageException;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for ApiExceptionHandler - verifies all exception types map to correct HTTP status
 * and RFC 7807 ProblemDetail format.
 */
@WebMvcTest(ApiExceptionHandlerTest.TestExceptionController.class)
@Import({ApiExceptionHandler.class, ApiExceptionHandlerTest.TestSecurityConfig.class,
        ApiExceptionHandlerTest.TestExceptionController.class})
class ApiExceptionHandlerTest {

    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(basic -> {});
            return http.build();
        }
    }

    /**
     * Test controller that throws each exception type for testing the handler.
     */
    @RestController
    @RequestMapping("/api/v1/test-errors")
    static class TestExceptionController {
        @GetMapping("/validation")
        public String throwValidation() {
            throw new ValidationException(List.of("field 'name' is invalid", "version not semver"));
        }

        @GetMapping("/not-found")
        public String throwNotFound() {
            throw new NotFoundException("Artifact not found: test-slug@1.0.0");
        }

        @GetMapping("/bad-request")
        public String throwBadRequest() {
            throw new IllegalArgumentException("Invalid type slug: foobar");
        }

        @GetMapping("/storage")
        public String throwStorage() {
            throw new StorageException("Git repository corrupted");
        }

        @GetMapping("/generic")
        public String throwGeneric() throws Exception {
            throw new RuntimeException("Something went very wrong");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Bucket loginRateLimitBucket;

    @Test
    @WithMockUser
    void validationException_returns422WithErrors() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/validation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]").value("field 'name' is invalid"))
                .andExpect(jsonPath("$.errors[1]").value("version not semver"));
    }

    @Test
    @WithMockUser
    void notFoundException_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Artifact not found: test-slug@1.0.0"));
    }

    @Test
    @WithMockUser
    void illegalArgumentException_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Invalid type slug: foobar"));
    }

    @Test
    @WithMockUser
    void storageException_returns500_noLeakedDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/storage"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Storage Error"))
                .andExpect(jsonPath("$.detail").value("An internal storage error occurred"));
    }

    @Test
    @WithMockUser
    void genericException_returns500() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}
