package com.agentlibrary.model;

import java.util.List;

/**
 * Configuration for how an artifact should be installed into a project.
 */
public record InstallConfig(
        String target,
        List<String> files,
        String merge
) {
    public InstallConfig {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
