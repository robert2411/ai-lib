package com.agentlibrary.storage;

import com.agentlibrary.model.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LocalGitArtifactRepositoryReadTest {

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

    // ======================== LIST tests ========================

    @Test
    void listWithNullFilterReturnsAll() {
        saveTestArtifact("skill-a", ArtifactType.SKILL, "1.0.0");
        saveTestArtifact("skill-b", ArtifactType.SKILL, "1.0.0");
        saveTestArtifact("agent-c", ArtifactType.AGENT_CLAUDE, "1.0.0");

        List<ArtifactMetadata> result = repo.list(null);
        assertEquals(3, result.size());
    }

    @Test
    void listWithEmptyFilterReturnsAll() {
        saveTestArtifact("skill-a", ArtifactType.SKILL, "1.0.0");
        saveTestArtifact("agent-b", ArtifactType.AGENT_CLAUDE, "1.0.0");

        Filter emptyFilter = new Filter(null, null, null, null);
        List<ArtifactMetadata> result = repo.list(emptyFilter);
        assertEquals(2, result.size());
    }

    @Test
    void listFiltersByType() {
        saveTestArtifact("skill-a", ArtifactType.SKILL, "1.0.0");
        saveTestArtifact("skill-b", ArtifactType.SKILL, "1.0.0");
        saveTestArtifact("agent-c", ArtifactType.AGENT_CLAUDE, "1.0.0");

        Filter filter = new Filter(ArtifactType.SKILL, null, null, null);
        List<ArtifactMetadata> result = repo.list(filter);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> m.type() == ArtifactType.SKILL));
    }

    @Test
    void listFiltersByHarness() {
        saveArtifactWithHarnesses("claude-only", ArtifactType.SKILL, List.of(Harness.CLAUDE));
        saveArtifactWithHarnesses("copilot-only", ArtifactType.SKILL, List.of(Harness.COPILOT));
        saveArtifactWithHarnesses("both", ArtifactType.SKILL, List.of(Harness.CLAUDE, Harness.COPILOT));

        Filter filter = new Filter(null, Harness.COPILOT, null, null);
        List<ArtifactMetadata> result = repo.list(filter);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> m.harnesses().contains(Harness.COPILOT)));
    }

    @Test
    void listFiltersByTag() {
        saveArtifactWithTags("git-skill", ArtifactType.SKILL, List.of("git", "workflow"));
        saveArtifactWithTags("web-skill", ArtifactType.SKILL, List.of("web", "html"));
        saveArtifactWithTags("mixed-skill", ArtifactType.SKILL, List.of("git", "web"));

        Filter filter = new Filter(null, null, "git", null);
        List<ArtifactMetadata> result = repo.list(filter);
        assertEquals(2, result.size());
    }

    @Test
    void listFiltersByQuery() {
        saveTestArtifact("git-helper", ArtifactType.SKILL, "1.0.0");
        saveTestArtifact("code-reviewer", ArtifactType.AGENT_CLAUDE, "1.0.0");

        Filter filter = new Filter(null, null, null, "git");
        List<ArtifactMetadata> result = repo.list(filter);
        assertEquals(1, result.size());
        assertEquals("git-helper", result.get(0).name());
    }

    @Test
    void listWithCombinedFilters() {
        saveArtifactWithHarnesses("skill-claude", ArtifactType.SKILL, List.of(Harness.CLAUDE));
        saveArtifactWithHarnesses("agent-claude", ArtifactType.AGENT_CLAUDE, List.of(Harness.CLAUDE));
        saveArtifactWithHarnesses("skill-copilot", ArtifactType.SKILL, List.of(Harness.COPILOT));

        Filter filter = new Filter(ArtifactType.SKILL, Harness.CLAUDE, null, null);
        List<ArtifactMetadata> result = repo.list(filter);
        assertEquals(1, result.size());
        assertEquals("skill-claude", result.get(0).name());
    }

    // ======================== VERSIONS tests ========================

    @Test
    void versionsReturnsSortedDescending() {
        User user = new User("alice", Set.of("admin"));
        saveTestArtifact("my-skill", ArtifactType.SKILL, "1.0.0");
        saveTestArtifact("my-skill", ArtifactType.SKILL, "1.1.0");
        saveTestArtifact("my-skill", ArtifactType.SKILL, "2.0.0");

        List<VersionRef> versions = repo.versions("skill", "my-skill");
        assertEquals(3, versions.size());
        assertEquals("2.0.0", versions.get(0).version());
        assertEquals("1.1.0", versions.get(1).version());
        assertEquals("1.0.0", versions.get(2).version());
    }

    @Test
    void versionsForNonExistentReturnsEmpty() {
        List<VersionRef> versions = repo.versions("skill", "nonexistent");
        assertTrue(versions.isEmpty());
    }

    @Test
    void versionsIncludesCommitIdAndTimestamp() {
        saveTestArtifact("versioned", ArtifactType.SKILL, "1.0.0");

        List<VersionRef> versions = repo.versions("skill", "versioned");
        assertEquals(1, versions.size());
        assertNotNull(versions.get(0).commitId());
        assertFalse(versions.get(0).commitId().isBlank());
        assertNotNull(versions.get(0).timestamp());
    }

    // ======================== DELETE tests ========================

    @Test
    void deleteRemovesArtifactFromTree() throws IOException {
        saveTestArtifact("to-delete", ArtifactType.SKILL, "1.0.0");
        User user = new User("alice", Set.of("admin"));

        repo.delete("skill", "to-delete", "removing", user);

        String content = repo.readFileFromHead("skills/to-delete/skill.md");
        assertEquals("", content);
    }

    @Test
    void deleteUpdatesIndexYaml() throws IOException {
        saveTestArtifact("keep-me", ArtifactType.SKILL, "1.0.0");
        saveTestArtifact("delete-me", ArtifactType.AGENT_CLAUDE, "1.0.0");
        User user = new User("alice", Set.of("admin"));

        repo.delete("agent-claude", "delete-me", "cleanup", user);

        String indexContent = repo.readFileFromHead("INDEX.yaml");
        List<ArtifactMetadata> entries = IndexFile.load(indexContent);
        assertEquals(1, entries.size());
        assertEquals("keep-me", entries.get(0).name());
    }

    @Test
    void deleteOfNonExistentThrows() {
        saveTestArtifact("something", ArtifactType.SKILL, "1.0.0");
        User user = new User("alice", Set.of("admin"));

        assertThrows(StorageException.class, () ->
                repo.delete("skill", "nonexistent", "oops", user));
    }

    @Test
    void tagsRemainAfterDelete() throws IOException {
        saveTestArtifact("tagged", ArtifactType.SKILL, "1.0.0");
        User user = new User("alice", Set.of("admin"));

        // Verify tag exists before delete
        Ref tagBefore = repo.getRepository().exactRef(Constants.R_TAGS + "skill/tagged@1.0.0");
        assertNotNull(tagBefore);

        repo.delete("skill", "tagged", "remove", user);

        // Tag should still exist after delete
        Ref tagAfter = repo.getRepository().exactRef(Constants.R_TAGS + "skill/tagged@1.0.0");
        assertNotNull(tagAfter);
    }

    // ======================== BUNDLE tests ========================

    @Test
    void bundleProducesValidTarGz() throws IOException {
        saveTestArtifact("bundled", ArtifactType.SKILL, "1.0.0");

        InputStream stream = repo.bundle("skill", "bundled", null);
        assertNotNull(stream);

        // Decompress and verify
        Map<String, String> files = extractTarGz(stream);
        assertTrue(files.containsKey("skill.md"));
        assertTrue(files.get("skill.md").contains("bundled"));
    }

    @Test
    void bundleAtSpecificRef() throws IOException {
        // Save v1 then v2 with different content
        Artifact v1 = createArtifact("versioned-bundle", ArtifactType.SKILL, "1.0.0", "# Version 1 content");
        Artifact v2 = createArtifact("versioned-bundle", ArtifactType.SKILL, "2.0.0", "# Version 2 content");
        User user = new User("alice", Set.of("admin"));
        repo.save(v1, "v1", user);
        repo.save(v2, "v2", user);

        // Bundle at v1
        InputStream stream = repo.bundle("skill", "versioned-bundle", "1.0.0");
        Map<String, String> files = extractTarGz(stream);
        assertTrue(files.get("skill.md").contains("Version 1 content"));
    }

    @Test
    void bundleOfNonExistentArtifactThrows() {
        saveTestArtifact("something", ArtifactType.SKILL, "1.0.0");

        assertThrows(StorageException.class, () ->
                repo.bundle("skill", "nonexistent", null));
    }

    @Test
    void bundleOfNonExistentVersionThrows() {
        saveTestArtifact("exists", ArtifactType.SKILL, "1.0.0");

        assertThrows(StorageException.class, () ->
                repo.bundle("skill", "exists", "9.9.9"));
    }

    @Test
    void bundleArchivePathsAreRelative() throws IOException {
        saveTestArtifact("relative-test", ArtifactType.SKILL, "1.0.0");

        InputStream stream = repo.bundle("skill", "relative-test", null);
        Map<String, String> files = extractTarGz(stream);

        // Should be "skill.md", NOT "skills/relative-test/skill.md"
        assertTrue(files.containsKey("skill.md"));
        assertFalse(files.containsKey("skills/relative-test/skill.md"));
    }

    // ======================== GET tests ========================

    @Test
    void getWithNullRefReturnsLatest() {
        Artifact v1 = createArtifact("get-test", ArtifactType.SKILL, "1.0.0", "# V1");
        Artifact v2 = createArtifact("get-test", ArtifactType.SKILL, "2.0.0", "# V2");
        User user = new User("alice", Set.of("admin"));
        repo.save(v1, "v1", user);
        repo.save(v2, "v2", user);

        Optional<Artifact> result = repo.get("skill", "get-test", null);
        assertTrue(result.isPresent());
        assertTrue(result.get().content().contains("# V2"));
    }

    @Test
    void getWithSpecificVersionReturnsIt() {
        Artifact v1 = createArtifact("versioned-get", ArtifactType.SKILL, "1.0.0", "# First");
        Artifact v2 = createArtifact("versioned-get", ArtifactType.SKILL, "2.0.0", "# Second");
        User user = new User("alice", Set.of("admin"));
        repo.save(v1, "v1", user);
        repo.save(v2, "v2", user);

        Optional<Artifact> result = repo.get("skill", "versioned-get", "1.0.0");
        assertTrue(result.isPresent());
        assertTrue(result.get().content().contains("# First"));
    }

    @Test
    void getNonExistentReturnsEmpty() {
        Optional<Artifact> result = repo.get("skill", "nothing", null);
        assertTrue(result.isEmpty());
    }

    // ======================== Helpers ========================

    private void saveTestArtifact(String name, ArtifactType type, String version) {
        Artifact artifact = createArtifact(name, type, version, "# " + name + "\nContent.");
        User user = new User("tester", Set.of("admin"));
        repo.save(artifact, "save " + name, user);
    }

    private void saveArtifactWithHarnesses(String name, ArtifactType type, List<Harness> harnesses) {
        ArtifactMetadata meta = new ArtifactMetadata(
                name, name, type, "1.0.0", "Description of " + name,
                harnesses, List.of("test"), "general",
                null, "author", Visibility.TEAM,
                Instant.parse("2026-04-19T10:00:00Z"),
                Instant.parse("2026-04-19T10:00:00Z"),
                null, null, null
        );
        Artifact artifact = new Artifact(meta, "# " + name);
        User user = new User("tester", Set.of("admin"));
        repo.save(artifact, "save", user);
    }

    private void saveArtifactWithTags(String name, ArtifactType type, List<String> tags) {
        ArtifactMetadata meta = new ArtifactMetadata(
                name, name, type, "1.0.0", "Description of " + name,
                List.of(Harness.CLAUDE), tags, "general",
                null, "author", Visibility.TEAM,
                Instant.parse("2026-04-19T10:00:00Z"),
                Instant.parse("2026-04-19T10:00:00Z"),
                null, null, null
        );
        Artifact artifact = new Artifact(meta, "# " + name);
        User user = new User("tester", Set.of("admin"));
        repo.save(artifact, "save", user);
    }

    private Artifact createArtifact(String name, ArtifactType type, String version, String body) {
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

    private Map<String, String> extractTarGz(InputStream inputStream) throws IOException {
        Map<String, String> files = new HashMap<>();
        try (GzipCompressorInputStream gzIn = new GzipCompressorInputStream(inputStream);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                byte[] content = tarIn.readAllBytes();
                files.put(entry.getName(), new String(content, StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
