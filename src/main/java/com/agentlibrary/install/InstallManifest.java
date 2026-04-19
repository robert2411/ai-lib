package com.agentlibrary.install;

import com.agentlibrary.model.Harness;

import java.util.List;

/**
 * Represents a complete install manifest containing entries for all requested artifacts
 * resolved for a specific harness. Serialises naturally to JSON via Jackson.
 */
public record InstallManifest(
        List<InstallEntry> entries,
        Harness harness
) {
    public InstallManifest {
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (harness == null) {
            throw new IllegalArgumentException("harness must not be null");
        }
    }
}
