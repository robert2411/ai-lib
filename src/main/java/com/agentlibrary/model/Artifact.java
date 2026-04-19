package com.agentlibrary.model;

import java.util.Objects;

/**
 * An artifact consisting of metadata and its raw content body.
 */
public record Artifact(
        ArtifactMetadata metadata,
        String content
) {
    public Artifact {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
