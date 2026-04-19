package com.agentlibrary.storage;

import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Git-backed implementation of ArtifactRepository using JGit bare repo APIs.
 * All write operations are serialised through the WriteQueue.
 */
public class LocalGitArtifactRepository implements ArtifactRepository {

    private static final Logger LOG = Logger.getLogger(LocalGitArtifactRepository.class.getName());

    private final AppProperties properties;
    private final WriteQueue writeQueue;
    private Repository repository;
    private Runnable refreshCallback;

    public LocalGitArtifactRepository(AppProperties properties, WriteQueue writeQueue) {
        this.properties = properties;
        this.writeQueue = writeQueue;
    }

    /**
     * Sets a callback invoked after each save/delete commit.
     * Used by IndexService to refresh the in-memory cache.
     */
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    /**
     * Initialises the bare git repository. Creates if absent, opens if existing.
     * Called by Spring as @PostConstruct.
     */
    @PostConstruct
    public void init() {
        try {
            Path repoDir = Path.of(properties.getRepoPath());
            if (Files.exists(repoDir.resolve("HEAD"))) {
                LOG.info("Opening existing bare repo at " + repoDir);
                this.repository = new FileRepositoryBuilder()
                        .setGitDir(repoDir.toFile())
                        .setBare()
                        .build();
            } else {
                LOG.info("Creating new bare repo at " + repoDir);
                Files.createDirectories(repoDir);
                this.repository = new FileRepositoryBuilder()
                        .setGitDir(repoDir.toFile())
                        .setBare()
                        .build();
                this.repository.create(true);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to initialise git repository", e);
        }
    }

    /**
     * Closes the repository and shuts down the write queue.
     */
    @PreDestroy
    public void close() {
        if (repository != null) {
            repository.close();
        }
        writeQueue.shutdown();
    }

    /**
     * Returns the underlying JGit Repository instance. Package-private for testing.
     */
    Repository getRepository() {
        return repository;
    }

    // ======================== SAVE (TASK-0003.04) ========================

    @Override
    public CommitResult save(Artifact artifact, String message, User user) {
        return writeQueue.submit(() -> doSave(artifact, message, user));
    }

    private CommitResult doSave(Artifact artifact, String message, User user) {
        try {
            ArtifactMetadata meta = artifact.metadata();
            String type = meta.type().folderName();
            String name = meta.name();
            String primaryFile = getPrimaryFileName(meta.type());
            String treePath = type + "/" + name + "/" + primaryFile;

            // 1. Encode artifact content (frontmatter + body)
            String fileContent = MetadataCodec.encode(artifact);
            byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);

            // 2. Insert blob
            ObjectId blobId = insertBlob(contentBytes);

            // 3. Build new tree
            ObjectId newTreeId = buildTree(treePath, blobId);

            // 4. Create commit
            String commitMsg = buildCommitMessage(meta, message);
            PersonIdent author = new PersonIdent(user.username(), user.username() + "@library");
            PersonIdent committer = new PersonIdent("agent-library", "server@agent-library");
            ObjectId commitId = createCommit(newTreeId, commitMsg, author, committer);

            // 5. Update HEAD
            updateHead(commitId);

            // 6. Create lightweight tag
            String tagName = meta.type().slug() + "/" + name + "@" + meta.version();
            createTag(tagName, commitId);

            // 7. Update INDEX.yaml
            updateIndex(meta, user);

            // 8. Notify index to refresh
            if (refreshCallback != null) {
                try {
                    refreshCallback.run();
                } catch (Exception e) {
                    LOG.warning("Refresh callback failed after save (data committed): " + e.getMessage());
                }
            }

            // Get timestamp from commit
            try (RevWalk rw = new RevWalk(repository)) {
                RevCommit commit = rw.parseCommit(commitId);
                Instant timestamp = Instant.ofEpochSecond(commit.getCommitTime());
                return new CommitResult(commitId.name(), timestamp, commitMsg);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to save artifact", e);
        }
    }

    // ======================== LIST (TASK-0003.05) ========================

    @Override
    public List<ArtifactMetadata> list(Filter filter) {
        try {
            String yaml = readFileFromHead("INDEX.yaml");
            List<ArtifactMetadata> all = IndexFile.load(yaml);
            return all.stream()
                    .filter(m -> matchesFilter(m, filter))
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Failed to list artifacts", e);
        }
    }

    private boolean matchesFilter(ArtifactMetadata meta, Filter filter) {
        if (filter == null) return true;
        if (filter.type() != null && meta.type() != filter.type()) return false;
        if (filter.harness() != null && !meta.harnesses().contains(filter.harness())) return false;
        if (filter.tag() != null && !meta.tags().contains(filter.tag())) return false;
        if (filter.query() != null && !filter.query().isBlank()) {
            String q = filter.query().toLowerCase();
            boolean matches = (meta.name() != null && meta.name().toLowerCase().contains(q))
                    || (meta.title() != null && meta.title().toLowerCase().contains(q))
                    || (meta.description() != null && meta.description().toLowerCase().contains(q));
            if (!matches) return false;
        }
        return true;
    }

    // ======================== GET (TASK-0003.05) ========================

    @Override
    public Optional<Artifact> get(String type, String name, String ref) {
        try {
            ArtifactType artType = ArtifactType.fromSlug(type);
            String primaryFile = getPrimaryFileName(artType);
            String path = artType.folderName() + "/" + name + "/" + primaryFile;

            ObjectId treeId = resolveTreeFromRef(type, name, ref);
            if (treeId == null) return Optional.empty();

            try (TreeWalk tw = TreeWalk.forPath(repository, path, treeId)) {
                if (tw == null) return Optional.empty();
                byte[] bytes = repository.open(tw.getObjectId(0)).getBytes();
                String content = new String(bytes, StandardCharsets.UTF_8);
                return Optional.of(MetadataCodec.decode(content));
            }
        } catch (IOException e) {
            throw new StorageException("Failed to get artifact " + type + "/" + name, e);
        }
    }

    // ======================== DELETE (TASK-0003.05) ========================

    @Override
    public void delete(String type, String name, String message, User user) {
        writeQueue.submit(() -> {
            doDelete(type, name, message, user);
            return null;
        });
    }

    private void doDelete(String type, String name, String message, User user) {
        try {
            ArtifactType artType = ArtifactType.fromSlug(type);
            String folderPath = artType.folderName() + "/" + name + "/";

            ObjectId headTree = getHeadTree();
            if (headTree == null) {
                throw new StorageException("Cannot delete from empty repository");
            }

            // Build new tree WITHOUT the artifact folder
            DirCache dc = DirCache.newInCore();
            DirCacheBuilder builder = dc.builder();

            boolean found = false;
            try (ObjectReader reader = repository.newObjectReader();
                 TreeWalk tw = new TreeWalk(reader)) {
                tw.addTree(headTree);
                tw.setRecursive(true);

                while (tw.next()) {
                    String path = tw.getPathString();
                    if (path.startsWith(folderPath)) {
                        found = true;
                        continue; // skip — deleting
                    }
                    DirCacheEntry entry = new DirCacheEntry(path);
                    entry.setObjectId(tw.getObjectId(0));
                    entry.setFileMode(tw.getFileMode(0));
                    builder.add(entry);
                }
            }

            if (!found) {
                throw new StorageException("Artifact not found: " + type + "/" + name);
            }

            builder.finish();

            ObjectId newTreeId;
            try (ObjectInserter inserter = repository.newObjectInserter()) {
                newTreeId = dc.writeTree(inserter);
                inserter.flush();
            }

            // Commit deletion
            String commitMsg = type + "/" + name + ": " + (message != null ? message : "delete");
            PersonIdent author = new PersonIdent(user.username(), user.username() + "@library");
            PersonIdent committer = new PersonIdent("agent-library", "server@agent-library");
            ObjectId commitId = createCommit(newTreeId, commitMsg, author, committer);
            updateHead(commitId);

            // Update INDEX.yaml (remove entry)
            String currentYaml = readFileFromHead("INDEX.yaml");
            List<ArtifactMetadata> entries = new ArrayList<>(IndexFile.load(currentYaml));
            entries.removeIf(e -> e.type() == artType && e.name().equals(name));
            String updatedYaml = IndexFile.save(entries);

            byte[] indexBytes = updatedYaml.getBytes(StandardCharsets.UTF_8);
            ObjectId blobId = insertBlob(indexBytes);
            ObjectId indexTreeId = buildTree("INDEX.yaml", blobId);
            PersonIdent serverIdent = new PersonIdent("agent-library", "server@agent-library");
            ObjectId indexCommitId = createCommit(indexTreeId, "chore: update INDEX.yaml", serverIdent, serverIdent);
            updateHead(indexCommitId);

            // Notify index to refresh
            if (refreshCallback != null) {
                try {
                    refreshCallback.run();
                } catch (Exception e) {
                    LOG.warning("Refresh callback failed after delete (data committed): " + e.getMessage());
                }
            }
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException("Failed to delete artifact " + type + "/" + name, e);
        }
    }

    // ======================== VERSIONS (TASK-0003.05) ========================

    @Override
    public List<VersionRef> versions(String type, String name) {
        try {
            String tagPrefix = type + "/" + name + "@";
            List<VersionRef> refs = new ArrayList<>();

            List<Ref> tagRefs = repository.getRefDatabase()
                    .getRefsByPrefix(Constants.R_TAGS + tagPrefix);

            try (RevWalk rw = new RevWalk(repository)) {
                for (Ref ref : tagRefs) {
                    String tagName = ref.getName().substring(Constants.R_TAGS.length());
                    String version = tagName.substring(tagName.lastIndexOf("@") + 1);

                    ObjectId commitId = ref.getObjectId();
                    RevCommit commit = rw.parseCommit(commitId);
                    Instant timestamp = Instant.ofEpochSecond(commit.getCommitTime());

                    refs.add(new VersionRef(version, commitId.name(), timestamp));
                    rw.reset();
                }
            }

            // Sort by SemVer descending
            refs.sort((a, b) -> SemVer.parse(b.version()).compareTo(SemVer.parse(a.version())));
            return Collections.unmodifiableList(refs);
        } catch (IOException e) {
            throw new StorageException("Failed to list versions for " + type + "/" + name, e);
        }
    }

    // ======================== BUNDLE (TASK-0003.05) ========================

    @Override
    public InputStream bundle(String type, String name, String ref) {
        try {
            ArtifactType artType = ArtifactType.fromSlug(type);
            String folderPath = artType.folderName() + "/" + name + "/";

            ObjectId treeId = resolveTreeFromRef(type, name, ref);
            if (treeId == null) {
                throw new StorageException("Cannot bundle from empty repository");
            }

            // Build tar.gz archive
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(baos);
                 TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzOut)) {
                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

                boolean found = false;
                try (TreeWalk tw = new TreeWalk(repository)) {
                    tw.addTree(treeId);
                    tw.setRecursive(true);

                    while (tw.next()) {
                        String path = tw.getPathString();
                        if (!path.startsWith(folderPath)) continue;
                        found = true;

                        // Strip folder prefix for archive-relative paths
                        String archivePath = path.substring(folderPath.length());

                        ObjectId blobId = tw.getObjectId(0);
                        byte[] content = repository.open(blobId).getBytes();

                        TarArchiveEntry tarEntry = new TarArchiveEntry(archivePath);
                        tarEntry.setSize(content.length);
                        tarOut.putArchiveEntry(tarEntry);
                        tarOut.write(content);
                        tarOut.closeArchiveEntry();
                    }
                }

                if (!found) {
                    throw new StorageException("Artifact not found: " + type + "/" + name);
                }

                tarOut.finish();
            }

            return new ByteArrayInputStream(baos.toByteArray());
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException("Failed to create bundle for " + type + "/" + name, e);
        }
    }

    // ======================== Internal helpers ========================

    private ObjectId resolveTreeFromRef(String type, String name, String ref) throws IOException {
        if (ref == null || ref.isBlank() || "latest".equals(ref)) {
            return getHeadTree();
        }
        // Resolve version ref via tag
        String tagRef = Constants.R_TAGS + type + "/" + name + "@" + ref;
        Ref tagObject = repository.exactRef(tagRef);
        if (tagObject == null) {
            if ("latest".equals(ref)) {
                return getHeadTree();
            }
            throw new StorageException("Version not found: " + type + "/" + name + "@" + ref);
        }
        try (RevWalk rw = new RevWalk(repository)) {
            RevCommit commit = rw.parseCommit(tagObject.getObjectId());
            return commit.getTree().getId();
        }
    }

    String getPrimaryFileName(ArtifactType type) {
        return switch (type) {
            case SKILL -> "skill.md";
            case AGENT_CLAUDE -> "agent.md";
            case AGENT_COPILOT -> "agent.md";
            case AGENT_GROUP -> "agent-group.yaml";
            case MCP -> "mcp.json";
            case COMMAND -> "command.md";
            case HOOK -> "hook.json";
            case CLAUDE_MD -> "snippet.md";
            case OPENCODE -> "config.yaml";
            case PROMPT -> "prompt.md";
        };
    }

    ObjectId insertBlob(byte[] content) throws IOException {
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, content);
            inserter.flush();
            return blobId;
        }
    }

    /**
     * Builds a new tree from the current HEAD tree, adding or replacing the file at 'path'.
     * Uses TreeWalk skip-and-add pattern to prevent duplicate DirCache entries on overwrite.
     */
    ObjectId buildTree(String path, ObjectId blobId) throws IOException {
        ObjectId headTreeId = getHeadTree();

        DirCache dc = DirCache.newInCore();
        DirCacheBuilder builder = dc.builder();

        // Copy existing entries, skipping the target path
        if (headTreeId != null) {
            try (ObjectReader reader = repository.newObjectReader();
                 TreeWalk tw = new TreeWalk(reader)) {
                tw.addTree(headTreeId);
                tw.setRecursive(true);

                while (tw.next()) {
                    String entryPath = tw.getPathString();
                    if (entryPath.equals(path)) {
                        continue; // skip — will be replaced
                    }
                    DirCacheEntry entry = new DirCacheEntry(entryPath);
                    entry.setObjectId(tw.getObjectId(0));
                    entry.setFileMode(tw.getFileMode(0));
                    builder.add(entry);
                }
            }
        }

        // Add new/updated entry
        DirCacheEntry newEntry = new DirCacheEntry(path);
        newEntry.setObjectId(blobId);
        newEntry.setFileMode(FileMode.REGULAR_FILE);
        builder.add(newEntry);

        builder.finish();

        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId treeId = dc.writeTree(inserter);
            inserter.flush();
            return treeId;
        }
    }

    ObjectId createCommit(ObjectId treeId, String message,
                          PersonIdent author, PersonIdent committer) throws IOException {
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            CommitBuilder cb = new CommitBuilder();
            cb.setTreeId(treeId);
            cb.setMessage(message);
            cb.setAuthor(author);
            cb.setCommitter(committer);

            ObjectId headId = resolveHead();
            if (headId != null) {
                cb.setParentId(headId);
            }

            ObjectId commitId = inserter.insert(cb);
            inserter.flush();
            return commitId;
        }
    }

    void updateHead(ObjectId commitId) throws IOException {
        RefUpdate ru = repository.updateRef(Constants.HEAD);
        ru.setNewObjectId(commitId);
        ru.setRefLogMessage("commit", false);
        ObjectId oldHead = resolveHead();
        if (oldHead == null) {
            ru.setExpectedOldObjectId(ObjectId.zeroId());
        } else {
            ru.setExpectedOldObjectId(oldHead);
        }
        RefUpdate.Result result = ru.update();
        if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FAST_FORWARD) {
            throw new StorageException("Failed to update HEAD: " + result);
        }
    }

    void createTag(String tagName, ObjectId commitId) throws IOException {
        String fullRef = Constants.R_TAGS + tagName;
        RefUpdate ru = repository.updateRef(fullRef);
        ru.setNewObjectId(commitId);
        ru.setRefLogMessage("tag", false);

        // Check if tag already exists
        Ref existing = repository.exactRef(fullRef);
        if (existing == null) {
            ru.setExpectedOldObjectId(ObjectId.zeroId());
        } else {
            ru.setExpectedOldObjectId(existing.getObjectId());
        }

        RefUpdate.Result result = ru.forceUpdate();
        if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED
                && result != RefUpdate.Result.NO_CHANGE) {
            throw new StorageException("Failed to create tag " + tagName + ": " + result);
        }
    }

    private void updateIndex(ArtifactMetadata newMeta, User user) throws IOException {
        // Read current INDEX.yaml
        String currentYaml = readFileFromHead("INDEX.yaml");

        // Parse, add/replace entry
        List<ArtifactMetadata> entries = new ArrayList<>(IndexFile.load(currentYaml));
        entries.removeIf(e -> e.type() == newMeta.type() && e.name().equals(newMeta.name()));
        entries.add(newMeta);

        // Serialize and commit
        String updatedYaml = IndexFile.save(entries);
        byte[] indexBytes = updatedYaml.getBytes(StandardCharsets.UTF_8);
        ObjectId blobId = insertBlob(indexBytes);
        ObjectId treeId = buildTree("INDEX.yaml", blobId);
        PersonIdent serverIdent = new PersonIdent("agent-library", "server@agent-library");
        ObjectId commitId = createCommit(treeId, "chore: update INDEX.yaml", serverIdent, serverIdent);
        updateHead(commitId);
    }

    private String buildCommitMessage(ArtifactMetadata meta, String message) {
        if (message != null && !message.isBlank()) {
            return meta.type().slug() + "/" + meta.name() + ": " + message;
        }
        return meta.type().slug() + "/" + meta.name() + ": save " + meta.name() + "@" + meta.version();
    }

    String readFileFromHead(String path) throws IOException {
        ObjectId headId = resolveHead();
        if (headId == null) return "";

        try (RevWalk rw = new RevWalk(repository)) {
            RevCommit commit = rw.parseCommit(headId);
            RevTree tree = commit.getTree();

            try (TreeWalk tw = TreeWalk.forPath(repository, path, tree)) {
                if (tw == null) return "";
                ObjectId blobId = tw.getObjectId(0);
                byte[] bytes = repository.open(blobId).getBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }

    ObjectId resolveHead() throws IOException {
        Ref headRef = repository.exactRef(Constants.HEAD);
        if (headRef == null) return null;
        return headRef.getObjectId();
    }

    ObjectId getHeadTree() throws IOException {
        ObjectId headId = resolveHead();
        if (headId == null) return null;
        try (RevWalk rw = new RevWalk(repository)) {
            return rw.parseCommit(headId).getTree().getId();
        }
    }
}
