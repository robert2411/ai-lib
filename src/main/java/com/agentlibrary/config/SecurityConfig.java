package com.agentlibrary.config;

import com.agentlibrary.storage.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Spring Security configuration.
 * Configures HTTP Basic authentication, role-based access, and CSRF disabling for API usage.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger LOG = Logger.getLogger(SecurityConfig.class.getName());

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> {})
                .formLogin(form -> form.loginPage("/login").permitAll())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/healthz").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/artifacts/**").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/artifacts/**").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/artifacts/**").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(AppProperties props) {
        Path usersFilePath = Path.of(props.getUsersFile());

        if (!Files.exists(usersFilePath)) {
            LOG.severe("Users file not found at " + usersFilePath.toAbsolutePath()
                    + ". Cannot start without authentication config.");
            throw new IllegalStateException(
                    "Users file not found at " + usersFilePath.toAbsolutePath()
                            + ". Create a users.yaml file or set library.users-file to a valid path.");
        }

        List<UserDetails> users = loadUsersFromYaml(usersFilePath);
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @SuppressWarnings("unchecked")
    private List<UserDetails> loadUsersFromYaml(Path path) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> data;

        try (InputStream is = Files.newInputStream(path)) {
            data = yaml.load(is);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read users file at " + path + ": " + e.getMessage(), e);
        }

        if (data == null || !data.containsKey("users")) {
            throw new IllegalStateException(
                    "Users file at " + path + " is malformed: missing 'users' key");
        }

        List<Map<String, Object>> userEntries = (List<Map<String, Object>>) data.get("users");
        List<UserDetails> userDetails = new ArrayList<>();

        for (Map<String, Object> entry : userEntries) {
            String username = (String) entry.get("username");
            String password = (String) entry.get("password");
            List<String> roles = (List<String>) entry.get("roles");

            if (username == null || password == null || roles == null) {
                throw new IllegalStateException(
                        "Malformed user entry in " + path + ": username, password, and roles are required");
            }

            UserDetails user = User.builder()
                    .username(username)
                    .password(password)  // Already bcrypt-encoded
                    .roles(roles.toArray(new String[0]))
                    .build();
            userDetails.add(user);
        }

        LOG.info("Loaded " + userDetails.size() + " users from " + path);
        return userDetails;
    }
}
