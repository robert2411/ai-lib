package com.agentlibrary.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import com.agentlibrary.storage.AppProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FileUserDetailsService}.
 */
class FileUserDetailsServiceTest {

    @TempDir
    Path tempDir;

    private FileUserDetailsService serviceWith(String yamlContent) throws IOException {
        Path file = tempDir.resolve("users.yaml");
        Files.writeString(file, yamlContent);
        AppProperties props = new AppProperties();
        props.setUsersFile(file.toString());
        UsersFile usersFile = new UsersFile(props);
        return new FileUserDetailsService(usersFile);
    }

    @Test
    void loadUserByUsername_validUser_returnsCorrectUserDetails() throws IOException {
        String yaml = """
                users:
                  - username: admin
                    password: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
                    roles:
                      - ADMIN
                      - EDITOR
                """;

        FileUserDetailsService service = serviceWith(yaml);
        UserDetails user = service.loadUserByUsername("admin");

        assertEquals("admin", user.getUsername());
        assertEquals("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy", user.getPassword());

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        assertEquals(2, authorities.size());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_EDITOR")));
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFoundException() throws IOException {
        String yaml = """
                users:
                  - username: admin
                    password: "$2a$10$hash"
                    roles:
                      - ADMIN
                """;

        FileUserDetailsService service = serviceWith(yaml);

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("nonexistent"));
    }

    @Test
    void loadUserByUsername_rolesMappedWithPrefix() throws IOException {
        String yaml = """
                users:
                  - username: viewer
                    password: "$2a$10$hash"
                    roles:
                      - USER
                """;

        FileUserDetailsService service = serviceWith(yaml);
        UserDetails user = service.loadUserByUsername("viewer");

        assertTrue(user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void loadUserByUsername_multipleUsers_eachAccessible() throws IOException {
        String yaml = """
                users:
                  - username: alice
                    password: "$2a$10$hashA"
                    roles:
                      - ADMIN
                  - username: bob
                    password: "$2a$10$hashB"
                    roles:
                      - USER
                """;

        FileUserDetailsService service = serviceWith(yaml);

        assertNotNull(service.loadUserByUsername("alice"));
        assertNotNull(service.loadUserByUsername("bob"));
    }
}
