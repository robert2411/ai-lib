package com.agentlibrary.storage;

import com.agentlibrary.model.*;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface defining the storage contract for artifacts.
 * Implementations manage persistence of artifacts in a git-backed store.
 */
public interface ArtifactRepository {

    /**
     * Lists artifact metadata matching the given filter criteria.
     *
     * @param filter filter criteria (nullable fields mean "no filter on that dimension")
     * @return list of matching artifact metadata
     */
    List<ArtifactMetadata> list(Filter filter);

    /**
     * Gets a specific artifact by type, name, and version ref.
     *
     * @param type the artifact type slug (e.g. "skill")
     * @param name the artifact name (e.g. "git-helper")
     * @param ref  version ref (null/empty/"latest" for latest, or a semver string)
     * @return the artifact if found, empty otherwise
     */
    Optional<Artifact> get(String type, String name, String ref);

    /**
     * Saves an artifact to the repository.
     *
     * @param artifact the artifact to save
     * @param message  commit message
     * @param user     the user performing the save
     * @return the commit result with commitId, timestamp, and message
     */
    CommitResult save(Artifact artifact, String message, User user);

    /**
     * Deletes an artifact from the repository.
     *
     * @param type    the artifact type slug
     * @param name    the artifact name
     * @param message commit message
     * @param user    the user performing the delete
     * @throws StorageException if artifact not found
     */
    void delete(String type, String name, String message, User user);

    /**
     * Lists all version refs for a given artifact.
     *
     * @param type the artifact type slug
     * @param name the artifact name
     * @return list of version refs sorted by SemVer descending
     */
    List<VersionRef> versions(String type, String name);

    /**
     * Creates a tar.gz bundle of a single artifact at a specific version.
     *
     * @param type the artifact type slug
     * @param name the artifact name
     * @param ref  version ref (null/empty/"latest" for latest, or a semver string)
     * @return input stream of the tar.gz archive
     * @throws StorageException if artifact or version not found
     */
    InputStream bundle(String type, String name, String ref);
}
