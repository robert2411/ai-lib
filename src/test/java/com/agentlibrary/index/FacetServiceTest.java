package com.agentlibrary.index;

import com.agentlibrary.model.*;
import com.agentlibrary.storage.ArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FacetServiceTest {

    private IndexService indexService;
    private FacetService facetService;
    private ArtifactRepository repository;

    private static ArtifactMetadata metadata(String name, ArtifactType type,
                                              List<Harness> harnesses, List<String> tags,
                                              String category, String author) {
        return new ArtifactMetadata(
                name, "Title " + name, type, "1.0.0", "Description",
                harnesses, tags, category, "java", author,
                null, null, null, null, null, null
        );
    }

    @BeforeEach
    void setUp() {
        repository = mock(ArtifactRepository.class);
        indexService = new IndexService(repository);
    }

    private void initWithArtifacts(List<ArtifactMetadata> artifacts) {
        when(repository.list(null)).thenReturn(artifacts);
        indexService.init();
        facetService = new FacetService(indexService);
    }

    @Test
    void getDistinctTags_returnsAllUniqueTags_sorted() {
        initWithArtifacts(List.of(
                metadata("a", ArtifactType.SKILL, List.of(), List.of("git", "automation"), "cat1", "bob"),
                metadata("b", ArtifactType.SKILL, List.of(), List.of("automation", "review"), "cat2", "alice"),
                metadata("c", ArtifactType.AGENT_CLAUDE, List.of(), List.of("deploy"), "cat1", "bob")
        ));

        List<String> tags = facetService.getDistinctTags();
        assertEquals(List.of("automation", "deploy", "git", "review"), tags);
    }

    @Test
    void getDistinctTypes_returnsOnlyPresentTypes() {
        initWithArtifacts(List.of(
                metadata("a", ArtifactType.SKILL, List.of(), List.of(), "cat", "author"),
                metadata("b", ArtifactType.AGENT_CLAUDE, List.of(), List.of(), "cat", "author"),
                metadata("c", ArtifactType.SKILL, List.of(), List.of(), "cat", "author")
        ));

        List<ArtifactType> types = facetService.getDistinctTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains(ArtifactType.SKILL));
        assertTrue(types.contains(ArtifactType.AGENT_CLAUDE));
    }

    @Test
    void getDistinctHarnesses_returnsOnlyPresentHarnesses() {
        initWithArtifacts(List.of(
                metadata("a", ArtifactType.SKILL, List.of(Harness.CLAUDE), List.of(), "cat", "author"),
                metadata("b", ArtifactType.SKILL, List.of(Harness.COPILOT), List.of(), "cat", "author"),
                metadata("c", ArtifactType.SKILL, List.of(Harness.CLAUDE, Harness.COPILOT), List.of(), "cat", "author")
        ));

        List<Harness> harnesses = facetService.getDistinctHarnesses();
        assertEquals(2, harnesses.size());
        assertTrue(harnesses.contains(Harness.CLAUDE));
        assertTrue(harnesses.contains(Harness.COPILOT));
    }

    @Test
    void getDistinctCategories_returnsUniqueSorted() {
        initWithArtifacts(List.of(
                metadata("a", ArtifactType.SKILL, List.of(), List.of(), "development", "author"),
                metadata("b", ArtifactType.SKILL, List.of(), List.of(), "automation", "author"),
                metadata("c", ArtifactType.SKILL, List.of(), List.of(), "development", "author")
        ));

        List<String> categories = facetService.getDistinctCategories();
        assertEquals(List.of("automation", "development"), categories);
    }

    @Test
    void getDistinctAuthors_returnsUniqueSorted() {
        initWithArtifacts(List.of(
                metadata("a", ArtifactType.SKILL, List.of(), List.of(), "cat", "charlie"),
                metadata("b", ArtifactType.SKILL, List.of(), List.of(), "cat", "alice"),
                metadata("c", ArtifactType.SKILL, List.of(), List.of(), "cat", "charlie")
        ));

        List<String> authors = facetService.getDistinctAuthors();
        assertEquals(List.of("alice", "charlie"), authors);
    }

    @Test
    void emptyIndex_returnsEmptyLists() {
        initWithArtifacts(List.of());

        assertTrue(facetService.getDistinctTags().isEmpty());
        assertTrue(facetService.getDistinctTypes().isEmpty());
        assertTrue(facetService.getDistinctHarnesses().isEmpty());
        assertTrue(facetService.getDistinctCategories().isEmpty());
        assertTrue(facetService.getDistinctAuthors().isEmpty());
    }

    @Test
    void getDistinctCategories_filtersNullAndBlank() {
        initWithArtifacts(List.of(
                metadata("a", ArtifactType.SKILL, List.of(), List.of(), null, "author"),
                metadata("b", ArtifactType.SKILL, List.of(), List.of(), "", "author"),
                metadata("c", ArtifactType.SKILL, List.of(), List.of(), "real-category", "author")
        ));

        List<String> categories = facetService.getDistinctCategories();
        assertEquals(List.of("real-category"), categories);
    }

    @Test
    void getDistinctAuthors_filtersNullAndBlank() {
        initWithArtifacts(List.of(
                metadata("a", ArtifactType.SKILL, List.of(), List.of(), "cat", null),
                metadata("b", ArtifactType.SKILL, List.of(), List.of(), "cat", ""),
                metadata("c", ArtifactType.SKILL, List.of(), List.of(), "cat", "real-author")
        ));

        List<String> authors = facetService.getDistinctAuthors();
        assertEquals(List.of("real-author"), authors);
    }
}
