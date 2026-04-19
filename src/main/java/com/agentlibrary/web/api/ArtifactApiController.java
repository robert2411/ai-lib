package com.agentlibrary.web.api;

import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.ResolvedGroupMember;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST controller for artifact CRUD operations.
 * Provides listing, search, get by slug/version, group member resolution,
 * and create/update/delete operations.
 */
@RestController
@RequestMapping("/api/v1/artifacts")
public class ArtifactApiController {

    private final ArtifactService artifactService;

    public ArtifactApiController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    // ===== READ ENDPOINTS =====

    /**
     * Lists artifacts with optional filtering and pagination.
     * If 'q' param is provided, delegates to full-text search.
     */
    @GetMapping
    public ResponseEntity<ArtifactListResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String harness,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ArtifactType artifactType = type != null ? ArtifactType.fromSlug(type) : null;
        Harness harnessEnum = harness != null ? Harness.fromSlug(harness) : null;
        Filter filter = new Filter(artifactType, harnessEnum, tag, q);

        List<ArtifactMetadata> results;
        long total;

        if (q != null && !q.isBlank()) {
            List<ArtifactMetadata> searchResults = artifactService.search(q, filter);
            total = searchResults.size();
            int fromIndex = Math.min(page * size, searchResults.size());
            int toIndex = Math.min(fromIndex + size, searchResults.size());
            results = searchResults.subList(fromIndex, toIndex);
        } else {
            results = artifactService.list(filter, page, size);
            total = artifactService.count(filter);
        }

        return ResponseEntity.ok(ArtifactListResponse.of(results, page, size, total));
    }

    /**
     * Gets a specific artifact by slug and version.
     */
    @GetMapping("/{slug}/{version}")
    public ResponseEntity<ArtifactResponse> get(
            @PathVariable String slug,
            @PathVariable String version) {
        Artifact artifact = artifactService.get(slug, version);
        return ResponseEntity.ok(ArtifactResponse.fromArtifact(artifact));
    }

    /**
     * Gets the latest version of an artifact by slug.
     */
    @GetMapping("/{slug}/latest")
    public ResponseEntity<ArtifactResponse> getLatest(@PathVariable String slug) {
        Artifact artifact = artifactService.get(slug, "latest");
        return ResponseEntity.ok(ArtifactResponse.fromArtifact(artifact));
    }

    /**
     * Gets ordered list of members for an agent-group artifact.
     * Returns 400 for non-group artifact types.
     */
    @GetMapping("/{slug}/members")
    public ResponseEntity<List<GroupMemberResponse>> getMembers(@PathVariable String slug) {
        List<ResolvedGroupMember> members = artifactService.resolveGroup(slug);
        List<GroupMemberResponse> response = members.stream()
                .map(GroupMemberResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    // ===== WRITE ENDPOINTS =====

    /**
     * Creates or updates an artifact (UPSERT semantics).
     * No 409 Conflict — if slug already exists, it is updated.
     */
    @PostMapping(consumes = {"text/plain", "application/json"})
    public ResponseEntity<ArtifactResponse> create(
            @RequestBody String rawContent,
            Principal principal) {
        User user = extractUser(principal);
        CommitResult result = artifactService.createOrUpdate(rawContent, user);

        // Decode to get slug and version for Location header and response
        Artifact artifact = MetadataCodec.decode(rawContent);
        String slug = artifact.metadata().name();
        String version = artifact.metadata().version();

        URI location = URI.create("/api/v1/artifacts/" + slug + "/" + version);
        return ResponseEntity.created(location)
                .body(ArtifactResponse.fromArtifact(artifact));
    }

    /**
     * Updates an artifact at a specific slug/version.
     * Verifies slug and version in path match those in content frontmatter.
     */
    @PutMapping(value = "/{slug}/{version}", consumes = {"text/plain", "application/json"})
    public ResponseEntity<ArtifactResponse> update(
            @PathVariable String slug,
            @PathVariable String version,
            @RequestBody String rawContent,
            Principal principal) {
        // Verify slug matches
        Artifact decoded = MetadataCodec.decode(rawContent);
        if (!slug.equals(decoded.metadata().name())) {
            throw new IllegalArgumentException(
                    "Slug in path '" + slug + "' does not match slug in content '"
                            + decoded.metadata().name() + "'");
        }

        // Verify version matches
        if (!version.equals(decoded.metadata().version())) {
            throw new IllegalArgumentException(
                    "Version in path '" + version + "' does not match version in content '"
                            + decoded.metadata().version() + "'");
        }

        User user = extractUser(principal);
        artifactService.createOrUpdate(rawContent, user);

        return ResponseEntity.ok(ArtifactResponse.fromArtifact(decoded));
    }

    /**
     * Deletes an artifact by slug (all versions).
     * Path: DELETE /api/v1/artifacts/{slug} — no version path variable.
     */
    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            Principal principal) {
        User user = extractUser(principal);
        artifactService.delete(slug, user);
        return ResponseEntity.noContent().build();
    }

    // ===== HELPERS =====

    private User extractUser(Principal principal) {
        if (principal instanceof Authentication auth) {
            Set<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                    .collect(Collectors.toSet());
            return new User(auth.getName(), roles);
        }
        return new User(principal.getName(), Set.of("USER"));
    }
}
