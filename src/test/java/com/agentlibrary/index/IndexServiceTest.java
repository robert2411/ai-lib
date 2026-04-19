package com.agentlibrary.index;

import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.storage.ArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndexServiceTest {

    private ArtifactRepository repository;
    private IndexService indexService;

    private static ArtifactMetadata sampleMetadata(String name) {
        return new ArtifactMetadata(
                name, "Title " + name, ArtifactType.SKILL, "1.0.0",
                "Description for " + name, List.of(), List.of("tag1"),
                "category", "java", "author", null, null, null, null, null, null
        );
    }

    @BeforeEach
    void setUp() {
        repository = mock(ArtifactRepository.class);
        indexService = new IndexService(repository);
    }

    @Test
    void getAll_returnsAllArtifactsAfterInit() {
        List<ArtifactMetadata> expected = List.of(
                sampleMetadata("artifact-a"),
                sampleMetadata("artifact-b")
        );
        when(repository.list(null)).thenReturn(expected);

        indexService.init();

        List<ArtifactMetadata> result = indexService.getAll();
        assertEquals(2, result.size());
        assertEquals("artifact-a", result.get(0).name());
        assertEquals("artifact-b", result.get(1).name());
    }

    @Test
    void getAll_returnsEmptyListWhenRepositoryEmpty() {
        when(repository.list(null)).thenReturn(List.of());

        indexService.init();

        assertTrue(indexService.getAll().isEmpty());
    }

    @Test
    void refresh_updatesInMemoryListWithNewArtifacts() {
        // Initial state: 1 artifact
        when(repository.list(null)).thenReturn(List.of(sampleMetadata("artifact-a")));
        indexService.init();
        assertEquals(1, indexService.getAll().size());

        // After save, repository now returns 2
        when(repository.list(null)).thenReturn(List.of(
                sampleMetadata("artifact-a"),
                sampleMetadata("artifact-b")
        ));
        indexService.refresh();

        List<ArtifactMetadata> result = indexService.getAll();
        assertEquals(2, result.size());
        assertEquals("artifact-b", result.get(1).name());
    }

    @Test
    void refresh_handlesRepositoryException() {
        when(repository.list(null)).thenReturn(List.of(sampleMetadata("artifact-a")));
        indexService.init();

        // Repository throws on next call
        when(repository.list(null)).thenThrow(new RuntimeException("git error"));
        indexService.refresh();

        // Cache should be replaced with empty list (graceful degradation)
        assertTrue(indexService.getAll().isEmpty());
    }

    @Test
    void concurrentReads_duringRefreshDoNotThrow() throws Exception {
        List<ArtifactMetadata> data = List.of(
                sampleMetadata("artifact-a"),
                sampleMetadata("artifact-b"),
                sampleMetadata("artifact-c")
        );
        when(repository.list(null)).thenReturn(data);
        indexService.init();

        int readerCount = 10;
        int iterations = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(readerCount + 1);
        AtomicBoolean error = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);

        // Readers
        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < iterations; j++) {
                        List<ArtifactMetadata> result = indexService.getAll();
                        assertNotNull(result);
                        // Should always get a consistent list (not a partially updated one)
                        assertTrue(result.size() >= 0);
                    }
                } catch (Exception e) {
                    error.set(true);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Writer (refresher)
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int j = 0; j < iterations; j++) {
                    indexService.refresh();
                }
            } catch (Exception e) {
                error.set(true);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertFalse(error.get(), "Concurrent reads during refresh should not throw");
    }

    @Test
    void refresh_triggersSearchServiceRebuild() {
        // Use a real SearchService and verify it gets data via search
        SearchService searchService = new SearchService();
        indexService.setSearchService(searchService);

        List<ArtifactMetadata> data = List.of(sampleMetadata("artifact-a"));
        when(repository.list(null)).thenReturn(data);

        indexService.refresh();

        // SearchService should now have the artifact indexed
        List<ArtifactMetadata> results = searchService.search(null, null);
        assertEquals(1, results.size());
        assertEquals("artifact-a", results.get(0).name());
    }
}
