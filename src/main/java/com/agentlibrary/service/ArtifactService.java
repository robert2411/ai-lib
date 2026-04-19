package com.agentlibrary.service;

import com.agentlibrary.index.IndexService;
import com.agentlibrary.index.SearchService;
import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.metadata.MetadataValidator;
import com.agentlibrary.metadata.ValidationException;
import com.agentlibrary.model.*;
import com.agentlibrary.storage.ArtifactRepository;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestration service coordinating MetadataCodec, MetadataValidator,
 * ArtifactRepository, IndexService, and SearchService.
 */
@Service
public class ArtifactService {

    private static final Map<String, Integer> ROLE_ORDER = Map.of(
            "manager", 0,
            "analyse", 1,
            "implementation", 2,
            "custom", 3
    );

    private final ArtifactRepository repository;
    private final IndexService indexService;
    private final SearchService searchService;

    public ArtifactService(ArtifactRepository repository,
                           IndexService indexService,
                           SearchService searchService) {
        this.repository = repository;
        this.indexService = indexService;
        this.searchService = searchService;
    }

    /**
     * Creates or updates an artifact from raw content (with YAML frontmatter).
     * Validates metadata before saving to repository.
     *
     * @param rawContent the full artifact content including frontmatter
     * @param user       the user performing the operation
     * @return the commit result
     * @throws ValidationException if metadata validation fails
     */
    public CommitResult createOrUpdate(String rawContent, User user) {
        Artifact artifact = MetadataCodec.decode(rawContent);
        MetadataValidator.validate(artifact.metadata());

        // For agent-group type, validate that all member slugs exist
        if (artifact.metadata().type() == ArtifactType.AGENT_GROUP) {
            validateGroupMembers(artifact.metadata());
        }

        String message = artifact.metadata().type().slug() + "/" + artifact.metadata().name()
                + ": create/update by " + user.username();
        return repository.save(artifact, message, user);
    }

    /**
     * Retrieves a specific artifact by slug and version ref.
     *
     * @param slug the artifact name/slug
     * @param ref  version ref ("latest" or a semver string)
     * @return the artifact
     * @throws NotFoundException if not found
     */
    public Artifact get(String slug, String ref) {
        ArtifactType type = resolveType(slug);
        return repository.get(type.slug(), slug, ref)
                .orElseThrow(() -> new NotFoundException(
                        "Artifact not found: " + slug + "@" + ref));
    }

    /**
     * Lists artifacts matching the given filter with pagination.
     *
     * @param filter the filter criteria
     * @param page   zero-based page number
     * @param size   page size
     * @return paginated list of artifact metadata
     */
    public List<ArtifactMetadata> list(Filter filter, int page, int size) {
        List<ArtifactMetadata> all = indexService.getAll();

        // Apply filters in-memory
        List<ArtifactMetadata> filtered = all.stream()
                .filter(m -> filter == null || filter.type() == null || m.type() == filter.type())
                .filter(m -> filter == null || filter.harness() == null || m.harnesses().contains(filter.harness()))
                .filter(m -> filter == null || filter.tag() == null || m.tags().contains(filter.tag()))
                .collect(Collectors.toList());

        // Pagination
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return filtered.subList(fromIndex, toIndex);
    }

    /**
     * Returns the total count of artifacts matching the given filter.
     */
    public long count(Filter filter) {
        List<ArtifactMetadata> all = indexService.getAll();
        return all.stream()
                .filter(m -> filter == null || filter.type() == null || m.type() == filter.type())
                .filter(m -> filter == null || filter.harness() == null || m.harnesses().contains(filter.harness()))
                .filter(m -> filter == null || filter.tag() == null || m.tags().contains(filter.tag()))
                .count();
    }

    /**
     * Searches artifacts with a text query and optional filter.
     *
     * @param query  the search query text
     * @param filter optional filter criteria
     * @return list of matching artifact metadata
     */
    public List<ArtifactMetadata> search(String query, Filter filter) {
        return searchService.search(query, filter);
    }

    /**
     * Deletes an artifact by slug.
     *
     * @param slug the artifact name/slug
     * @param user the user performing the delete
     * @throws NotFoundException if slug is unknown
     */
    public void delete(String slug, User user) {
        ArtifactType type = resolveType(slug);
        String message = type.slug() + "/" + slug + ": deleted by " + user.username();
        repository.delete(type.slug(), slug, message, user);
    }

    /**
     * Lists all version refs for a given artifact.
     *
     * @param slug the artifact name/slug
     * @return list of version refs
     * @throws NotFoundException if slug is unknown
     */
    public List<VersionRef> getVersions(String slug) {
        ArtifactType type = resolveType(slug);
        return repository.versions(type.slug(), slug);
    }

    /**
     * Creates a tar.gz bundle of a single artifact at a specific version.
     *
     * @param slug the artifact name/slug
     * @param ref  version ref
     * @return input stream of the tar.gz archive
     * @throws NotFoundException if slug is unknown
     */
    public InputStream bundle(String slug, String ref) {
        ArtifactType type = resolveType(slug);
        return repository.bundle(type.slug(), slug, ref);
    }

    /**
     * Resolves an agent-group into its ordered member list.
     * Members are sorted by role order: manager, analyse, implementation, custom.
     *
     * @param slug the agent-group artifact slug
     * @return ordered list of resolved group members
     * @throws IllegalArgumentException if the artifact is not an agent-group type
     * @throws NotFoundException        if slug is unknown or a member slug is unknown
     */
    public List<ResolvedGroupMember> resolveGroup(String slug) {
        Artifact artifact = get(slug, "latest");

        if (artifact.metadata().type() != ArtifactType.AGENT_GROUP) {
            throw new IllegalArgumentException(
                    "Artifact '" + slug + "' is not an agent-group type");
        }

        List<ResolvedGroupMember> members = new ArrayList<>();
        for (AgentGroupMember member : artifact.metadata().members()) {
            Artifact memberArtifact = get(member.slug(), "latest");
            members.add(new ResolvedGroupMember(
                    member.slug(),
                    member.role(),
                    memberArtifact.metadata()
            ));
        }

        // Sort by role order: manager=0, analyse=1, implementation=2, custom=3
        members.sort(Comparator.comparingInt(m -> ROLE_ORDER.getOrDefault(m.role(), 99)));
        return members;
    }

    /**
     * Resolves the ArtifactType for a given slug by searching the index.
     */
    private ArtifactType resolveType(String slug) {
        return indexService.getAll().stream()
                .filter(m -> m.name().equals(slug))
                .map(ArtifactMetadata::type)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Unknown artifact: " + slug));
    }

    /**
     * Validates that all member slugs in an agent-group exist in the index.
     */
    private void validateGroupMembers(ArtifactMetadata metadata) {
        List<String> missing = new ArrayList<>();
        for (AgentGroupMember member : metadata.members()) {
            boolean exists = indexService.getAll().stream()
                    .anyMatch(m -> m.name().equals(member.slug()));
            if (!exists) {
                missing.add(member.slug());
            }
        }
        if (!missing.isEmpty()) {
            throw new ValidationException(
                    List.of("Agent group references non-existent members: " + missing));
        }
    }
}
