package com.agentlibrary.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link UserDetailsService} implementation backed by a YAML users file.
 * Delegates to {@link UsersFile} for loading user credentials and roles.
 *
 * <p>Users are loaded once at construction time and cached in memory.</p>
 */
@Service
public class FileUserDetailsService implements UserDetailsService {

    private final Map<String, UsersFile.UserEntry> usersByUsername;

    public FileUserDetailsService(UsersFile usersFile) {
        this.usersByUsername = usersFile.loadAll().stream()
                .collect(Collectors.toMap(UsersFile.UserEntry::username, entry -> entry));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UsersFile.UserEntry entry = usersByUsername.get(username);
        if (entry == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        var authorities = entry.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        return new User(entry.username(), entry.encodedPassword(), authorities);
    }
}
