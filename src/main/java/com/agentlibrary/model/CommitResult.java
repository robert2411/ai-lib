package com.agentlibrary.model;

import java.time.Instant;

/**
 * Represents the result of a git commit operation.
 */
public record CommitResult(
        String commitId,
        Instant timestamp,
        String message
) {
    public CommitResult {
        if (commitId == null || commitId.isBlank()) {
            throw new IllegalArgumentException("commitId must not be null or blank");
        }
    }
}
