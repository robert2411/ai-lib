package com.agentlibrary.install;

import com.agentlibrary.model.ArtifactType;

/**
 * Represents a single entry in an install manifest.
 * Each entry maps an artifact to its source path in the bundle and resolved target path for a harness.
 * The role field is populated for agent-group member entries (nullable for non-group installs).
 */
public record InstallEntry(
        String slug,
        ArtifactType type,
        String sourcePath,
        String targetPath,
        String role
) {
    /**
     * Convenience constructor for non-group entries (role = null).
     */
    public InstallEntry(String slug, ArtifactType type, String sourcePath, String targetPath) {
        this(slug, type, sourcePath, targetPath, null);
    }

    public InstallEntry {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be null or blank");
        }
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("targetPath must not be null or blank");
        }
    }
}
