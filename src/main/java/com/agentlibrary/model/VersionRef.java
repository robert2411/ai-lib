package com.agentlibrary.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a reference to a specific version in the artifact history.
 */
public record VersionRef(
        String version,
        String commitId,
        Instant timestamp
) {
    public VersionRef {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be null or blank");
        }
        if (commitId == null || commitId.isBlank()) {
            throw new IllegalArgumentException("commitId must not be null or blank");
        }
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
