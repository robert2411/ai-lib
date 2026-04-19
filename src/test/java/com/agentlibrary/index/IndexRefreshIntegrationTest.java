package com.agentlibrary.index;

import com.agentlibrary.model.*;
import com.agentlibrary.storage.ArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying the IndexService → SearchService → FacetService refresh chain.
 * Uses a mocked repository but real IndexService, SearchService, and FacetService instances.
 */
class IndexRefreshIntegrationTest {

    private ArtifactRepository repository;
    private IndexService indexService;
    private SearchService searchService;
    private FacetService facetService;

    private static ArtifactMetadata metadata(String name, ArtifactType type,
                                              List<Harness> harnesses, List<String> tags) {
        return new ArtifactMetadata(
                name, "Title " + name, type, "1.0.0", "Description of " + name,
                harnesses, tags, "general", "java", "author",
                null, null, null, null, null, null
        );
    }

    @BeforeEach
    void setUp() {
        repository = mock(ArtifactRepository.class);
        indexService = new IndexService(repository);
        searchService = new SearchService();
        facetService = new FacetService(indexService);

        // Wire SearchService into IndexService (like IndexConfig does)
        indexService.setSearchService(searchService);
    }

    @Test
    void afterRefresh_searchServiceReflectsNewArtifact() {
        // Initial state: one artifact
        when(repository.list(null)).thenReturn(List.of(
                metadata("git-helper", ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of("git"))
        ));
        indexService.init();

        // Verify initial state
        List<ArtifactMetadata> initialResults = searchService.search("git", null);
        assertEquals(1, initialResults.size());
        assertEquals("git-helper", initialResults.get(0).name());

        // Simulate: new artifact saved, repository now returns 2
        when(repository.list(null)).thenReturn(List.of(
                metadata("git-helper", ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of("git")),
                metadata("code-review", ArtifactType.AGENT_CLAUDE, List.of(Harness.COPILOT), List.of("review"))
        ));

        // Trigger refresh (simulates what the repository callback does)
        indexService.refresh();

        // SearchService should now find the new artifact
        List<ArtifactMetadata> newResults = searchService.search("review", null);
        assertEquals(1, newResults.size());
        assertEquals("code-review", newResults.get(0).name());

        // Old artifact still searchable
        List<ArtifactMetadata> oldResults = searchService.search("git", null);
        assertEquals(1, oldResults.size());
    }

    @Test
    void afterRefresh_facetServiceReflectsNewTags() {
        // Initial state
        when(repository.list(null)).thenReturn(List.of(
                metadata("tool-a", ArtifactType.SKILL, List.of(), List.of("git"))
        ));
        indexService.init();

        assertEquals(List.of("git"), facetService.getDistinctTags());

        // Add new artifact with new tags
        when(repository.list(null)).thenReturn(List.of(
                metadata("tool-a", ArtifactType.SKILL, List.of(), List.of("git")),
                metadata("tool-b", ArtifactType.AGENT_CLAUDE, List.of(Harness.COPILOT), List.of("review", "ci"))
        ));
        indexService.refresh();

        List<String> tags = facetService.getDistinctTags();
        assertEquals(List.of("ci", "git", "review"), tags);

        // Types should include both
        List<ArtifactType> types = facetService.getDistinctTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains(ArtifactType.SKILL));
        assertTrue(types.contains(ArtifactType.AGENT_CLAUDE));
    }

    @Test
    void afterRefresh_facetServiceReflectsNewHarnesses() {
        when(repository.list(null)).thenReturn(List.of(
                metadata("tool-a", ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of())
        ));
        indexService.init();

        assertEquals(List.of(Harness.CLAUDE), facetService.getDistinctHarnesses());

        // Add copilot artifact
        when(repository.list(null)).thenReturn(List.of(
                metadata("tool-a", ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of()),
                metadata("tool-b", ArtifactType.SKILL, List.of(Harness.COPILOT), List.of())
        ));
        indexService.refresh();

        List<Harness> harnesses = facetService.getDistinctHarnesses();
        assertEquals(2, harnesses.size());
        assertTrue(harnesses.contains(Harness.CLAUDE));
        assertTrue(harnesses.contains(Harness.COPILOT));
    }

    @Test
    void refreshCallbackWiring_simulatesFullChain() {
        // Simulate what IndexConfig.wireIndexRefreshCallback does
        when(repository.list(null)).thenReturn(List.of(
                metadata("initial", ArtifactType.SKILL, List.of(), List.of("start"))
        ));
        indexService.init();
        searchService.rebuild(indexService.getAll());

        // Verify initial search works
        assertEquals(1, searchService.search("initial", null).size());

        // Now simulate a repository save triggering the callback
        when(repository.list(null)).thenReturn(List.of(
                metadata("initial", ArtifactType.SKILL, List.of(), List.of("start")),
                metadata("new-artifact", ArtifactType.MCP, List.of(Harness.CLAUDE), List.of("new"))
        ));

        // This is what the repository's refreshCallback.run() does
        indexService.refresh();

        // Verify full chain: search finds new artifact
        List<ArtifactMetadata> results = searchService.search("new-artifact", null);
        assertEquals(1, results.size());
        assertEquals("new-artifact", results.get(0).name());

        // Facets updated
        assertTrue(facetService.getDistinctTags().contains("new"));
        assertTrue(facetService.getDistinctTypes().contains(ArtifactType.MCP));
    }
}
