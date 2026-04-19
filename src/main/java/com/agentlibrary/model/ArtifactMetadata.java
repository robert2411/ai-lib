package com.agentlibrary.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Core metadata record for a library artifact.
 * Contains all frontmatter fields that describe an artifact's identity and configuration.
 */
public record ArtifactMetadata(
        String name,
        String title,
        ArtifactType type,
        String version,
        String description,
        List<Harness> harnesses,
        List<String> tags,
        String category,
        String language,
        String author,
        Visibility visibility,
        Instant created,
        Instant updated,
        InstallConfig install,
        List<AgentGroupMember> members,
        Map<String, Object> extra
) {
    public ArtifactMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be null or blank");
        }
        harnesses = harnesses == null ? List.of() : List.copyOf(harnesses);
        tags = tags == null ? List.of() : List.copyOf(tags);
        members = members == null ? List.of() : List.copyOf(members);
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }
}
