package com.agentlibrary.index;

import com.agentlibrary.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {

    private SearchService searchService;

    private static ArtifactMetadata metadata(String name, String title, String description,
                                              ArtifactType type, List<Harness> harnesses,
                                              List<String> tags) {
        return new ArtifactMetadata(
                name, title, type, "1.0.0", description,
                harnesses, tags, "general", "java", "testauthor",
                null, null, null, null, null, null
        );
    }

    @BeforeEach
    void setUp() {
        searchService = new SearchService();
    }

    @Test
    void search_returnsRelevantResultsForKeywordInTitle() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("git-helper", "Git Helper Skill", "Helps with git commands",
                        ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of("git")),
                metadata("code-review", "Code Review Agent", "Reviews pull requests",
                        ArtifactType.AGENT_CLAUDE, List.of(Harness.COPILOT), List.of("review"))
        );
        searchService.rebuild(artifacts);

        List<ArtifactMetadata> results = searchService.search("git", null);
        assertEquals(1, results.size());
        assertEquals("git-helper", results.get(0).name());
    }

    @Test
    void search_returnsRelevantResultsForKeywordInDescription() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("git-helper", "Git Helper", "Helps with version control commands",
                        ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of("git")),
                metadata("code-review", "Code Review", "Reviews pull requests for quality",
                        ArtifactType.AGENT_CLAUDE, List.of(Harness.COPILOT), List.of("review"))
        );
        searchService.rebuild(artifacts);

        List<ArtifactMetadata> results = searchService.search("pull requests", null);
        assertEquals(1, results.size());
        assertEquals("code-review", results.get(0).name());
    }

    @Test
    void search_respectsTypeFilter() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("skill-a", "Skill A", "Does stuff",
                        ArtifactType.SKILL, List.of(), List.of()),
                metadata("agent-a", "Agent A", "Does stuff",
                        ArtifactType.AGENT_CLAUDE, List.of(), List.of()),
                metadata("skill-b", "Skill B", "Does other stuff",
                        ArtifactType.SKILL, List.of(), List.of())
        );
        searchService.rebuild(artifacts);

        Filter filter = new Filter(ArtifactType.SKILL, null, null, null);
        List<ArtifactMetadata> results = searchService.search("stuff", filter);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(m -> m.type() == ArtifactType.SKILL));
    }

    @Test
    void search_respectsHarnessFilter() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("tool-a", "Tool A", "A helpful tool",
                        ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of()),
                metadata("tool-b", "Tool B", "Another helpful tool",
                        ArtifactType.SKILL, List.of(Harness.COPILOT), List.of()),
                metadata("tool-c", "Tool C", "Yet another tool",
                        ArtifactType.SKILL, List.of(Harness.CLAUDE, Harness.COPILOT), List.of())
        );
        searchService.rebuild(artifacts);

        Filter filter = new Filter(null, Harness.COPILOT, null, null);
        List<ArtifactMetadata> results = searchService.search("tool", filter);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(m -> m.harnesses().contains(Harness.COPILOT)));
    }

    @Test
    void search_respectsTagFilter() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("tool-a", "Tool A", "A tool",
                        ArtifactType.SKILL, List.of(), List.of("automation", "git")),
                metadata("tool-b", "Tool B", "Another tool",
                        ArtifactType.SKILL, List.of(), List.of("review")),
                metadata("tool-c", "Tool C", "Third tool",
                        ArtifactType.SKILL, List.of(), List.of("automation"))
        );
        searchService.rebuild(artifacts);

        Filter filter = new Filter(null, null, "automation", null);
        List<ArtifactMetadata> results = searchService.search(null, filter);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(m -> m.tags().contains("automation")));
    }

    @Test
    void search_emptyQueryWithFilterReturnsAllMatching() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("skill-a", "Skill A", "First skill",
                        ArtifactType.SKILL, List.of(), List.of("tag1")),
                metadata("agent-a", "Agent A", "First agent",
                        ArtifactType.AGENT_CLAUDE, List.of(), List.of("tag1")),
                metadata("skill-b", "Skill B", "Second skill",
                        ArtifactType.SKILL, List.of(), List.of("tag2"))
        );
        searchService.rebuild(artifacts);

        // Empty query with type filter
        Filter filter = new Filter(ArtifactType.SKILL, null, null, null);
        List<ArtifactMetadata> results = searchService.search("", filter);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(m -> m.type() == ArtifactType.SKILL));
    }

    @Test
    void search_rebuiltIndexReflectsNewArtifacts() {
        // Initial index
        List<ArtifactMetadata> initial = List.of(
                metadata("old-tool", "Old Tool", "Legacy tool",
                        ArtifactType.SKILL, List.of(), List.of())
        );
        searchService.rebuild(initial);

        List<ArtifactMetadata> firstResults = searchService.search("old", null);
        assertEquals(1, firstResults.size());

        // Rebuild with new data
        List<ArtifactMetadata> updated = List.of(
                metadata("new-tool", "New Tool", "Modern tool",
                        ArtifactType.SKILL, List.of(), List.of())
        );
        searchService.rebuild(updated);

        // Old data should be gone
        List<ArtifactMetadata> oldResults = searchService.search("old", null);
        assertTrue(oldResults.isEmpty());

        // New data should be found
        List<ArtifactMetadata> newResults = searchService.search("new", null);
        assertEquals(1, newResults.size());
        assertEquals("new-tool", newResults.get(0).name());
    }

    @Test
    void search_beforeRebuildReturnsEmptyList() {
        List<ArtifactMetadata> results = searchService.search("anything", null);
        assertTrue(results.isEmpty());
    }

    @Test
    void search_invalidQueryDoesNotThrow() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("tool-a", "Tool A", "A tool",
                        ArtifactType.SKILL, List.of(), List.of())
        );
        searchService.rebuild(artifacts);

        // Invalid Lucene query syntax should not throw
        assertDoesNotThrow(() -> {
            searchService.search("AND OR NOT ][}{", null);
        });
    }

    @Test
    void search_nullQueryAndNullFilterReturnsAll() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("tool-a", "Tool A", "A tool",
                        ArtifactType.SKILL, List.of(), List.of()),
                metadata("tool-b", "Tool B", "Another tool",
                        ArtifactType.AGENT_CLAUDE, List.of(), List.of())
        );
        searchService.rebuild(artifacts);

        List<ArtifactMetadata> results = searchService.search(null, null);
        assertEquals(2, results.size());
    }

    @Test
    void search_combinesTextQueryWithMultipleFilters() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("skill-git", "Git Skill", "Git operations",
                        ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of("git")),
                metadata("agent-git", "Git Agent", "Git automation",
                        ArtifactType.AGENT_CLAUDE, List.of(Harness.CLAUDE), List.of("git")),
                metadata("skill-review", "Review Skill", "Code review",
                        ArtifactType.SKILL, List.of(Harness.COPILOT), List.of("review"))
        );
        searchService.rebuild(artifacts);

        // Query "git" + type=SKILL + harness=CLAUDE
        Filter filter = new Filter(ArtifactType.SKILL, Harness.CLAUDE, null, null);
        List<ArtifactMetadata> results = searchService.search("git", filter);

        assertEquals(1, results.size());
        assertEquals("skill-git", results.get(0).name());
    }

    @Test
    void rebuild_closesOldResourcesOnSubsequentCall() {
        // First rebuild creates initial resources
        List<ArtifactMetadata> initial = List.of(
                metadata("tool-a", "Tool A", "First tool",
                        ArtifactType.SKILL, List.of(), List.of())
        );
        searchService.rebuild(initial);

        // Verify search works
        assertEquals(1, searchService.search("tool", null).size());

        // Second rebuild should close old resources without error
        List<ArtifactMetadata> updated = List.of(
                metadata("tool-b", "Tool B", "Second tool",
                        ArtifactType.SKILL, List.of(), List.of())
        );
        searchService.rebuild(updated);

        // Old index gone, new one works
        assertTrue(searchService.search("First", null).isEmpty());
        assertEquals(1, searchService.search("Second", null).size());
    }

    @Test
    void rebuild_multipleTimesDoesNotLeak() {
        // Repeatedly rebuild to ensure old resources are released each time
        for (int i = 0; i < 10; i++) {
            List<ArtifactMetadata> artifacts = List.of(
                    metadata("tool-" + i, "Tool " + i, "Iteration " + i,
                            ArtifactType.SKILL, List.of(), List.of())
            );
            searchService.rebuild(artifacts);
        }

        // Only the final iteration should be searchable by unique name
        List<ArtifactMetadata> results = searchService.search(null, null);
        assertEquals(1, results.size());
        assertEquals("tool-9", results.get(0).name());
    }

    @Test
    void close_releasesResourcesAndReturnsEmpty() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("tool-a", "Tool A", "A tool",
                        ArtifactType.SKILL, List.of(), List.of())
        );
        searchService.rebuild(artifacts);
        assertEquals(1, searchService.search("tool", null).size());

        // Close should release resources
        searchService.close();

        // After close, search returns empty
        assertTrue(searchService.search("tool", null).isEmpty());
    }

    @Test
    void close_whenNeverRebuilt_doesNotThrow() {
        // Closing a never-built service should not throw
        assertDoesNotThrow(() -> searchService.close());
    }

    @Test
    void close_calledTwice_doesNotThrow() {
        List<ArtifactMetadata> artifacts = List.of(
                metadata("tool-a", "Tool A", "A tool",
                        ArtifactType.SKILL, List.of(), List.of())
        );
        searchService.rebuild(artifacts);
        searchService.close();

        // Second close should be safe (idempotent)
        assertDoesNotThrow(() -> searchService.close());
    }
}
