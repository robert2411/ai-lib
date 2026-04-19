package com.agentlibrary.web.api;

import com.agentlibrary.model.ArtifactMetadata;

import java.util.List;

/**
 * Response DTO for a paginated list of artifacts.
 */
public record ArtifactListResponse(
        List<ArtifactResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /**
     * Creates a paginated response from metadata list with pagination info.
     */
    public static ArtifactListResponse of(List<ArtifactMetadata> items, int page, int size, long total) {
        List<ArtifactResponse> responseItems = items.stream()
                .map(ArtifactResponse::fromMetadata)
                .toList();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new ArtifactListResponse(responseItems, page, size, total, totalPages);
    }
}
