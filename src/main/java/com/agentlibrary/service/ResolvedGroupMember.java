package com.agentlibrary.service;

import com.agentlibrary.model.ArtifactMetadata;

/**
 * Represents a resolved member of an agent group, including its full metadata.
 */
public record ResolvedGroupMember(
        String slug,
        String role,
        ArtifactMetadata metadata
) {
    public ResolvedGroupMember {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be null or blank");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be null or blank");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
    }
}
