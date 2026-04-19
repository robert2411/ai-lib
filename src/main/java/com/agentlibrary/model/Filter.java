package com.agentlibrary.model;

/**
 * Filter criteria for artifact listing operations.
 * All fields are optional (nullable).
 */
public record Filter(
        ArtifactType type,
        Harness harness,
        String tag,
        String query
) {
}
