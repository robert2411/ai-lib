package com.agentlibrary.model;

import java.util.Set;

/**
 * Represents a member of an agent group, with a slug identifying the agent
 * and a role defining its function within the group.
 */
public record AgentGroupMember(
        String slug,
        String role
) {
    /** Recognised roles for agent group members. */
    public static final Set<String> RECOGNISED_ROLES = Set.of("manager", "implementation", "analyse", "custom");

    public AgentGroupMember {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be null or blank");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be null or blank");
        }
    }

    /**
     * Returns true if this member's role is one of the recognised roles.
     */
    public boolean hasRecognisedRole() {
        return RECOGNISED_ROLES.contains(role);
    }
}
