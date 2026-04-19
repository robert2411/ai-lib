package com.agentlibrary.web.api;

import com.agentlibrary.model.Artifact;
import com.agentlibrary.model.ArtifactMetadata;

import java.util.List;

/**
 * Response DTO for a single artifact.
 */
public record ArtifactResponse(
        String name,
        String title,
        String type,
        String version,
        String description,
        List<String> harnesses,
        List<String> tags,
        String category,
        String language,
        String author,
        String visibility,
        String created,
        String updated,
        String content
) {
    /**
     * Creates an ArtifactResponse from a full Artifact (includes content body).
     */
    public static ArtifactResponse fromArtifact(Artifact artifact) {
        ArtifactMetadata m = artifact.metadata();
        return new ArtifactResponse(
                m.name(),
                m.title(),
                m.type().slug(),
                m.version(),
                m.description(),
                m.harnesses().stream().map(h -> h.slug()).toList(),
                m.tags(),
                m.category(),
                m.language(),
                m.author(),
                m.visibility() != null ? m.visibility().slug() : null,
                m.created() != null ? m.created().toString() : null,
                m.updated() != null ? m.updated().toString() : null,
                artifact.content()
        );
    }

    /**
     * Creates an ArtifactResponse from metadata only (no content body).
     */
    public static ArtifactResponse fromMetadata(ArtifactMetadata m) {
        return new ArtifactResponse(
                m.name(),
                m.title(),
                m.type().slug(),
                m.version(),
                m.description(),
                m.harnesses().stream().map(h -> h.slug()).toList(),
                m.tags(),
                m.category(),
                m.language(),
                m.author(),
                m.visibility() != null ? m.visibility().slug() : null,
                m.created() != null ? m.created().toString() : null,
                m.updated() != null ? m.updated().toString() : null,
                null
        );
    }
}
