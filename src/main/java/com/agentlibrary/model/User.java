package com.agentlibrary.model;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a user of the library system.
 */
public record User(
        String username,
        Set<String> roles
) {
    public User {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        Objects.requireNonNull(roles, "roles must not be null");
        roles = Set.copyOf(roles); // defensive copy
    }
}
