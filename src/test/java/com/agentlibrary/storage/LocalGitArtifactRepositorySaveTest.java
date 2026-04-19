package com.agentlibrary.storage;

import com.agentlibrary.model.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class LocalGitArtifactRepositorySaveTest {

    @TempDir
    Path tempDir;

    private LocalGitArtifactRepository repo;

    @BeforeEach
    void setUp() {
        Path repoPath = tempDir.resolve("test.git");
        AppProperties props = new AppProperties();
        props.setRepoPath(repoPath.toString());
        WriteQueue writeQueue = new WriteQueue();
        repo = new LocalGitArtifactRepository(props, writeQueue);
        repo.init();
    }

    @AfterEach
    void tearDown() {
        repo.close();
    }

    @Test
    void savePersistsArtifactAndCreatesTag() throws IOException {
        Artifact artifact = createTestArtifact("git-helper", ArtifactType.SKILL, "1.0.0", "# Git Helper\nA skill.");
        User user = new User("alice", Set.of("admin"));

        CommitResult result = repo.save(artifact, "initial save", user);

        // Verify commit result
        assertNotNull(result);
        assertNotNull(result.commitId());
        assertFalse(result.commitId().isBlank());
        assertNotNull(result.timestamp());
        assertTrue(result.message().contains("git-helper"));

        // Verify file exists in HEAD tree
        String content = repo.readFileFromHead("skills/git-helper/skill.md");
        assertFalse(content.isBlank());
        assertTrue(content.contains("git-helper"));
        assertTrue(content.contains("# Git Helper"));

        // Verify tag exists
        Ref tag = repo.getRepository().exactRef(Constants.R_TAGS + "skill/git-helper@1.0.0");
        assertNotNull(tag, "Tag should exist");
    }

    @Test
    void saveWithDuplicateVersionOverwrites() throws IOException {
        User user = new User("bob", Set.of("user"));

        // First save
        Artifact v1 = createTestArtifact("my-skill", ArtifactType.SKILL, "1.0.0", "# Version 1");
        repo.save(v1, "first", user);

        // Second save with same version but different content
        Artifact v1Updated = createTestArtifact("my-skill", ArtifactType.SKILL, "1.0.0", "# Version 1 Updated");
        CommitResult result = repo.save(v1Updated, "update", user);

        assertNotNull(result);

        // Verify updated content
        String content = repo.readFileFromHead("skills/my-skill/skill.md");
        assertTrue(content.contains("Version 1 Updated"));
        assertFalse(content.contains("# Version 1\n"));

        // Verify no duplicate entries in tree
        ObjectId treeId = repo.getHeadTree();
        int count = 0;
        try (TreeWalk tw = new TreeWalk(repo.getRepository())) {
            tw.addTree(treeId);
            tw.setRecursive(true);
            while (tw.next()) {
                if (tw.getPathString().equals("skills/my-skill/skill.md")) {
                    count++;
                }
            }
        }
        assertEquals(1, count, "Should have exactly one entry for the file");

        // Verify tag points to latest
        Ref tag = repo.getRepository().exactRef(Constants.R_TAGS + "skill/my-skill@1.0.0");
        assertNotNull(tag);
    }

    @Test
    void indexYamlUpdatedAfterSave() {
        Artifact artifact = createTestArtifact("test-skill", ArtifactType.SKILL, "1.0.0", "# Test");
        User user = new User("alice", Set.of("admin"));

        repo.save(artifact, "save it", user);

        // Read INDEX.yaml from HEAD
        try {
            String indexContent = repo.readFileFromHead("INDEX.yaml");
            assertFalse(indexContent.isBlank());
            List<ArtifactMetadata> entries = IndexFile.load(indexContent);
            assertEquals(1, entries.size());
            assertEquals("test-skill", entries.get(0).name());
            assertEquals(ArtifactType.SKILL, entries.get(0).type());
            assertEquals("1.0.0", entries.get(0).version());
        } catch (IOException e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    @Test
    void indexYamlCorrectOnMultipleSaves() {
        User user = new User("alice", Set.of("admin"));

        // Save first artifact
        Artifact skill1 = createTestArtifact("skill-a", ArtifactType.SKILL, "1.0.0", "# A");
        repo.save(skill1, "save a", user);

        // Save different artifact
        Artifact skill2 = createTestArtifact("skill-b", ArtifactType.AGENT_CLAUDE, "2.0.0", "# B");
        repo.save(skill2, "save b", user);

        // Save updated version of first
        Artifact skill1v2 = createTestArtifact("skill-a", ArtifactType.SKILL, "1.1.0", "# A v2");
        repo.save(skill1v2, "update a", user);

        try {
            String indexContent = repo.readFileFromHead("INDEX.yaml");
            List<ArtifactMetadata> entries = IndexFile.load(indexContent);
            assertEquals(2, entries.size());

            // Should have skill-b and updated skill-a
            boolean foundA = entries.stream().anyMatch(e -> e.name().equals("skill-a") && e.version().equals("1.1.0"));
            boolean foundB = entries.stream().anyMatch(e -> e.name().equals("skill-b"));
            assertTrue(foundA, "Should have updated skill-a entry");
            assertTrue(foundB, "Should have skill-b entry");
        } catch (IOException e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    @Test
    void concurrentSavesViaWriteQueue() throws Exception {
        User user = new User("alice", Set.of("admin"));
        int numSaves = 10;
        ExecutorService pool = Executors.newFixedThreadPool(numSaves);
        List<Future<CommitResult>> futures = new ArrayList<>();

        for (int i = 0; i < numSaves; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                Artifact art = createTestArtifact("skill-" + index, ArtifactType.SKILL,
                        "1.0.0", "# Skill " + index);
                return repo.save(art, "save " + index, user);
            }));
        }

        pool.shutdown();

        // Verify all succeeded
        for (Future<CommitResult> f : futures) {
            assertNotNull(f.get());
        }

        // Verify INDEX has all 10
        String indexContent = repo.readFileFromHead("INDEX.yaml");
        List<ArtifactMetadata> entries = IndexFile.load(indexContent);
        assertEquals(numSaves, entries.size());
    }

    @Test
    void commitMessageFormatIsCorrect() throws IOException {
        Artifact artifact = createTestArtifact("helper", ArtifactType.SKILL, "1.0.0", "# Helper");
        User user = new User("alice", Set.of("admin"));

        repo.save(artifact, "initial release", user);

        // Read commit from git log (skip INDEX.yaml commit, get save commit)
        ObjectId headId = repo.resolveHead();
        try (RevWalk rw = new RevWalk(repo.getRepository())) {
            RevCommit indexCommit = rw.parseCommit(headId);
            RevCommit saveCommit = rw.parseCommit(indexCommit.getParent(0));
            assertEquals("skill/helper: initial release", saveCommit.getFullMessage());
        }
    }

    @Test
    void authorAndCommitterAreSetCorrectly() throws IOException {
        Artifact artifact = createTestArtifact("my-art", ArtifactType.COMMAND, "1.0.0", "# Command");
        User user = new User("alice", Set.of("admin"));

        repo.save(artifact, "test", user);

        // Check the save commit (parent of HEAD, since HEAD is index update)
        ObjectId headId = repo.resolveHead();
        try (RevWalk rw = new RevWalk(repo.getRepository())) {
            RevCommit indexCommit = rw.parseCommit(headId);
            RevCommit saveCommit = rw.parseCommit(indexCommit.getParent(0));
            assertEquals("alice", saveCommit.getAuthorIdent().getName());
            assertEquals("alice@library", saveCommit.getAuthorIdent().getEmailAddress());
            assertEquals("agent-library", saveCommit.getCommitterIdent().getName());
            assertEquals("server@agent-library", saveCommit.getCommitterIdent().getEmailAddress());
        }
    }

    @Test
    void saveToEmptyRepoFirstCommit() {
        Artifact artifact = createTestArtifact("first", ArtifactType.SKILL, "0.1.0", "# First");
        User user = new User("pioneer", Set.of("admin"));

        CommitResult result = repo.save(artifact, "genesis", user);

        assertNotNull(result);
        assertNotNull(result.commitId());

        // Verify HEAD exists and tree is correct
        try {
            ObjectId headId = repo.resolveHead();
            assertNotNull(headId);
            try (RevWalk rw = new RevWalk(repo.getRepository())) {
                RevCommit head = rw.parseCommit(headId);
                // HEAD is the index commit, its parent is the save commit
                RevCommit saveCommit = rw.parseCommit(head.getParent(0));
                assertEquals(0, saveCommit.getParentCount()); // first commit has no parent
            }
        } catch (IOException e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    private Artifact createTestArtifact(String name, ArtifactType type, String version, String body) {
        ArtifactMetadata meta = new ArtifactMetadata(
                name, name, type, version, "Description of " + name,
                List.of(Harness.CLAUDE), List.of("test"), "general",
                null, "author", Visibility.TEAM,
                Instant.parse("2026-04-19T10:00:00Z"),
                Instant.parse("2026-04-19T10:00:00Z"),
                null, null, null
        );
        return new Artifact(meta, body);
    }
}
