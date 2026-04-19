package com.agentlibrary.storage;

import com.agentlibrary.index.IndexService;
import com.agentlibrary.index.SearchService;
import com.agentlibrary.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for refresh callback behaviour in LocalGitArtifactRepository.
 * Covers:
 * - Callback exceptions do not propagate (data integrity preserved)
 * - Real save triggers search index update end-to-end
 */
class LocalGitArtifactRepositoryCallbackTest {

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
    void save_callbackExceptionDoesNotPropagate() throws Exception {
        // Set a callback that throws
        repo.setRefreshCallback(() -> {
            throw new RuntimeException("Simulated callback failure");
        });

        Artifact artifact = createTestArtifact("my-skill", ArtifactType.SKILL, "1.0.0", "# Skill");
        User user = new User("alice", Set.of("admin"));

        // Save should succeed despite callback exception
        CommitResult result = assertDoesNotThrow(() -> repo.save(artifact, "test save", user));

        assertNotNull(result);
        assertNotNull(result.commitId());
        assertFalse(result.commitId().isBlank());

        // Verify data was committed (callback failure didn't roll back)
        String content = repo.readFileFromHead("skills/my-skill/skill.md");
        assertNotNull(content);
        assertTrue(content.contains("# Skill"));
    }

    @Test
    void save_callbackIsInvokedOnSuccess() {
        AtomicBoolean called = new AtomicBoolean(false);
        repo.setRefreshCallback(() -> called.set(true));

        Artifact artifact = createTestArtifact("cb-test", ArtifactType.SKILL, "1.0.0", "# Test");
        User user = new User("bob", Set.of("user"));

        repo.save(artifact, "save it", user);

        assertTrue(called.get(), "Callback should have been invoked after save");
    }

    @Test
    void save_endToEnd_searchServiceReflectsNewArtifact() {
        // Wire up the real index/search chain
        SearchService searchService = new SearchService();
        IndexService indexService = new IndexService(repo);
        indexService.setSearchService(searchService);
        indexService.init();

        // Set the refresh callback to rebuild the index (mimics IndexConfig wiring)
        repo.setRefreshCallback(indexService::refresh);

        // Initially: no artifacts
        assertTrue(searchService.search("helper", null).isEmpty());

        // Save an artifact
        Artifact artifact = createTestArtifact("git-helper", ArtifactType.SKILL, "1.0.0", "# Git Helper");
        User user = new User("alice", Set.of("admin"));
        CommitResult result = repo.save(artifact, "initial save", user);
        assertNotNull(result);

        // Search should now find the new artifact via the real callback chain
        List<ArtifactMetadata> results = searchService.search("git", null);
        assertEquals(1, results.size());
        assertEquals("git-helper", results.get(0).name());
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
