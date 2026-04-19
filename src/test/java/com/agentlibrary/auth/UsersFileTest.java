package com.agentlibrary.auth;

import com.agentlibrary.storage.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UsersFile}.
 */
class UsersFileTest {

    @TempDir
    Path tempDir;

    private UsersFile usersFileWith(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        if (content != null) {
            Files.writeString(file, content);
        }
        AppProperties props = new AppProperties();
        props.setUsersFile(file.toString());
        return new UsersFile(props);
    }

    private UsersFile usersFileForMissing(String filename) {
        AppProperties props = new AppProperties();
        props.setUsersFile(tempDir.resolve(filename).toString());
        return new UsersFile(props);
    }

    @Test
    void loadAll_validYaml_returnsExpectedEntries() throws IOException {
        String yaml = """
                users:
                  - username: admin
                    password: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
                    roles:
                      - ADMIN
                      - EDITOR
                  - username: reader
                    password: "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ12"
                    roles:
                      - USER
                """;

        UsersFile usersFile = usersFileWith("users.yaml", yaml);
        List<UsersFile.UserEntry> entries = usersFile.loadAll();

        assertEquals(2, entries.size());

        UsersFile.UserEntry admin = entries.get(0);
        assertEquals("admin", admin.username());
        assertEquals("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy", admin.encodedPassword());
        assertEquals(List.of("ADMIN", "EDITOR"), admin.roles());

        UsersFile.UserEntry reader = entries.get(1);
        assertEquals("reader", reader.username());
        assertEquals(List.of("USER"), reader.roles());
    }

    @Test
    void loadAll_missingFile_throwsDescriptiveException() {
        UsersFile usersFile = usersFileForMissing("nonexistent.yaml");

        IllegalStateException ex = assertThrows(IllegalStateException.class, usersFile::loadAll);

        assertTrue(ex.getMessage().contains("Users file not found"),
                "Expected 'Users file not found' in message, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("nonexistent.yaml"),
                "Expected file path in message, got: " + ex.getMessage());
    }

    @Test
    void loadAll_malformedYaml_throwsDescriptiveException() throws IOException {
        UsersFile usersFile = usersFileWith("bad.yaml", "users:\n  - username: admin\n\t\tbad: [unclosed");

        IllegalStateException ex = assertThrows(IllegalStateException.class, usersFile::loadAll);

        assertTrue(ex.getMessage().contains("invalid YAML"),
                "Expected 'invalid YAML' in message, got: " + ex.getMessage());
    }

    @Test
    void loadAll_emptyFile_throwsException() throws IOException {
        UsersFile usersFile = usersFileWith("empty.yaml", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, usersFile::loadAll);

        assertTrue(ex.getMessage().contains("missing 'users' key"),
                "Expected missing users key message, got: " + ex.getMessage());
    }

    @Test
    void loadAll_missingUsersKey_throwsException() throws IOException {
        UsersFile usersFile = usersFileWith("nokey.yaml", "other_key: value\n");

        IllegalStateException ex = assertThrows(IllegalStateException.class, usersFile::loadAll);

        assertTrue(ex.getMessage().contains("missing 'users' key"),
                "Expected missing users key message, got: " + ex.getMessage());
    }

    @Test
    void loadAll_userMissingUsername_throwsException() throws IOException {
        String yaml = """
                users:
                  - password: "$2a$10$hash"
                    roles:
                      - ADMIN
                """;
        UsersFile usersFile = usersFileWith("nousername.yaml", yaml);

        IllegalStateException ex = assertThrows(IllegalStateException.class, usersFile::loadAll);

        assertTrue(ex.getMessage().contains("username"),
                "Expected message mentioning 'username', got: " + ex.getMessage());
    }

    @Test
    void loadAll_userMissingPassword_throwsException() throws IOException {
        String yaml = """
                users:
                  - username: admin
                    roles:
                      - ADMIN
                """;
        UsersFile usersFile = usersFileWith("nopwd.yaml", yaml);

        IllegalStateException ex = assertThrows(IllegalStateException.class, usersFile::loadAll);

        assertTrue(ex.getMessage().contains("password"),
                "Expected message mentioning 'password', got: " + ex.getMessage());
    }

    @Test
    void loadAll_userMissingRoles_throwsException() throws IOException {
        String yaml = """
                users:
                  - username: admin
                    password: "$2a$10$hash"
                """;
        UsersFile usersFile = usersFileWith("noroles.yaml", yaml);

        IllegalStateException ex = assertThrows(IllegalStateException.class, usersFile::loadAll);

        assertTrue(ex.getMessage().contains("roles"),
                "Expected message mentioning 'roles', got: " + ex.getMessage());
    }

    @Test
    void loadAll_emptyUsersList_returnsEmptyList() throws IOException {
        String yaml = """
                users: []
                """;
        UsersFile usersFile = usersFileWith("empty-list.yaml", yaml);

        List<UsersFile.UserEntry> entries = usersFile.loadAll();

        assertTrue(entries.isEmpty());
    }

    @Test
    void loadAll_nullUsersList_returnsEmptyList() throws IOException {
        String yaml = """
                users:
                """;
        UsersFile usersFile = usersFileWith("null-users.yaml", yaml);

        List<UsersFile.UserEntry> entries = usersFile.loadAll();

        assertTrue(entries.isEmpty());
    }

    @Test
    void userEntry_rolesAreImmutable() throws IOException {
        String yaml = """
                users:
                  - username: admin
                    password: "$2a$10$hash"
                    roles:
                      - ADMIN
                """;
        UsersFile usersFile = usersFileWith("immutable.yaml", yaml);

        List<UsersFile.UserEntry> entries = usersFile.loadAll();
        UsersFile.UserEntry entry = entries.get(0);

        assertThrows(UnsupportedOperationException.class,
                () -> entry.roles().add("HACKER"));
    }
}
