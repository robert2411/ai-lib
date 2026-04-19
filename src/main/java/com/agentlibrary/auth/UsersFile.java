package com.agentlibrary.auth;

import com.agentlibrary.storage.AppProperties;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads user credentials from a YAML file at startup.
 * The file path is resolved from {@link AppProperties#getUsersFile()}.
 *
 * <p>Expected YAML structure:</p>
 * <pre>
 * users:
 *   - username: admin
 *     password: "$2a$10$..."
 *     roles:
 *       - ADMIN
 * </pre>
 */
@Component
public class UsersFile {

    private final Path usersFilePath;

    /**
     * A user entry parsed from the YAML file.
     */
    public record UserEntry(String username, String encodedPassword, List<String> roles) {
        public UserEntry {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("username must not be blank");
            }
            if (encodedPassword == null || encodedPassword.isBlank()) {
                throw new IllegalArgumentException("encodedPassword must not be blank");
            }
            if (roles == null || roles.isEmpty()) {
                throw new IllegalArgumentException("roles must not be empty");
            }
            roles = List.copyOf(roles);
        }
    }

    public UsersFile(AppProperties props) {
        this.usersFilePath = Path.of(props.getUsersFile());
    }

    /**
     * Parses the YAML users file and returns an immutable list of user entries.
     *
     * @return list of user entries (may be empty if users list is empty)
     * @throws IllegalStateException if the file is missing, malformed, or has invalid entries
     */
    @SuppressWarnings("unchecked")
    public List<UserEntry> loadAll() {
        if (!Files.exists(usersFilePath)) {
            throw new IllegalStateException(
                    "Users file not found at " + usersFilePath.toAbsolutePath()
                            + ". Create a users.yaml file or set library.users-file to a valid path.");
        }

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> data;

        try (InputStream is = Files.newInputStream(usersFilePath)) {
            data = yaml.load(is);
        } catch (YAMLException e) {
            throw new IllegalStateException(
                    "Users file at " + usersFilePath + " contains invalid YAML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read users file at " + usersFilePath + ": " + e.getMessage(), e);
        }

        if (data == null || !data.containsKey("users")) {
            throw new IllegalStateException(
                    "Users file at " + usersFilePath + " is malformed: missing 'users' key. "
                            + "Expected format: users: [{username: ..., password: '<bcrypt hash>', roles: [...]}]");
        }

        Object usersObj = data.get("users");
        if (usersObj == null) {
            return Collections.emptyList();
        }

        if (!(usersObj instanceof List<?> userList)) {
            throw new IllegalStateException(
                    "Users file at " + usersFilePath + " is malformed: 'users' must be a list.");
        }

        if (userList.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserEntry> entries = new ArrayList<>();
        for (Object item : userList) {
            if (!(item instanceof Map<?, ?> entryMap)) {
                throw new IllegalStateException(
                        "Users file at " + usersFilePath + " is malformed: each user entry must be a map.");
            }

            Map<String, Object> entry = (Map<String, Object>) entryMap;
            String username = (String) entry.get("username");
            String password = (String) entry.get("password");
            List<String> roles = (List<String>) entry.get("roles");

            if (username == null || username.isBlank()) {
                throw new IllegalStateException(
                        "Malformed user entry in " + usersFilePath
                                + ": 'username' is required and must not be blank.");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalStateException(
                        "Malformed user entry in " + usersFilePath
                                + ": 'password' (bcrypt hash) is required for user '" + username + "'.");
            }
            if (roles == null || roles.isEmpty()) {
                throw new IllegalStateException(
                        "Malformed user entry in " + usersFilePath
                                + ": 'roles' (non-empty list) is required for user '" + username + "'.");
            }

            entries.add(new UserEntry(username, password, roles));
        }

        return Collections.unmodifiableList(entries);
    }
}
