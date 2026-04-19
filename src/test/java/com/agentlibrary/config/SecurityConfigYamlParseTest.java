package com.agentlibrary.config;

import com.agentlibrary.storage.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityConfig YAML parsing error handling.
 * Verifies that malformed YAML and missing files produce clear IllegalStateException messages.
 */
class SecurityConfigYamlParseTest {

    @TempDir
    Path tempDir;

    private AppProperties propsWithFile(String filename) {
        AppProperties props = new AppProperties();
        props.setUsersFile(tempDir.resolve(filename).toString());
        return props;
    }

    @Test
    void missingUsersFile_throwsIllegalStateException() {
        AppProperties props = propsWithFile("nonexistent.yaml");
        SecurityConfig config = new SecurityConfig();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.userDetailsService(props));

        assertTrue(ex.getMessage().contains("Users file not found"),
                "Expected message about missing file, got: " + ex.getMessage());
    }

    @Test
    void malformedYaml_throwsIllegalStateException() throws IOException {
        // Write invalid YAML (tab indentation mixed with spaces, unclosed quotes)
        Path usersFile = tempDir.resolve("bad.yaml");
        Files.writeString(usersFile, "users:\n  - username: admin\n\t\tbad: [unclosed");

        AppProperties props = new AppProperties();
        props.setUsersFile(usersFile.toString());

        SecurityConfig config = new SecurityConfig();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.userDetailsService(props));

        assertTrue(ex.getMessage().contains("invalid YAML"),
                "Expected message about invalid YAML, got: " + ex.getMessage());
    }

    @Test
    void emptyYaml_throwsIllegalStateException() throws IOException {
        Path usersFile = tempDir.resolve("empty.yaml");
        Files.writeString(usersFile, "");

        AppProperties props = new AppProperties();
        props.setUsersFile(usersFile.toString());

        SecurityConfig config = new SecurityConfig();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.userDetailsService(props));

        assertTrue(ex.getMessage().contains("malformed") || ex.getMessage().contains("missing 'users' key"),
                "Expected message about missing users key, got: " + ex.getMessage());
    }

    @Test
    void yamlWithoutUsersKey_throwsIllegalStateException() throws IOException {
        Path usersFile = tempDir.resolve("nokey.yaml");
        Files.writeString(usersFile, "other_key: value\n");

        AppProperties props = new AppProperties();
        props.setUsersFile(usersFile.toString());

        SecurityConfig config = new SecurityConfig();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.userDetailsService(props));

        assertTrue(ex.getMessage().contains("missing 'users' key"),
                "Expected message about missing 'users' key, got: " + ex.getMessage());
    }

    @Test
    void userEntryMissingPassword_throwsIllegalStateException() throws IOException {
        Path usersFile = tempDir.resolve("nopwd.yaml");
        Files.writeString(usersFile, """
                users:
                  - username: admin
                    roles:
                      - ADMIN
                """);

        AppProperties props = new AppProperties();
        props.setUsersFile(usersFile.toString());

        SecurityConfig config = new SecurityConfig();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.userDetailsService(props));

        assertTrue(ex.getMessage().contains("password"),
                "Expected message mentioning 'password' field, got: " + ex.getMessage());
    }

    @Test
    void validYaml_loadsSuccessfully() throws IOException {
        Path usersFile = tempDir.resolve("valid.yaml");
        Files.writeString(usersFile, """
                users:
                  - username: testuser
                    password: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
                    roles:
                      - USER
                """);

        AppProperties props = new AppProperties();
        props.setUsersFile(usersFile.toString());

        SecurityConfig config = new SecurityConfig();

        // Should not throw
        assertNotNull(config.userDetailsService(props));
    }
}
