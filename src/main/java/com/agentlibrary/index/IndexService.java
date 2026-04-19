package com.agentlibrary.index;

import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.storage.ArtifactRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * In-memory cache of all ArtifactMetadata loaded from INDEX.yaml.
 * Thread-safe reads via ReadWriteLock; refresh() called after each commit.
 */
@Service
public class IndexService {

    private static final Logger LOG = Logger.getLogger(IndexService.class.getName());

    private final ArtifactRepository repository;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile List<ArtifactMetadata> cache = List.of();

    private SearchService searchService;

    public IndexService(ArtifactRepository repository) {
        this.repository = repository;
    }

    /**
     * Sets the SearchService dependency (set via config to avoid circular DI).
     */
    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostConstruct
    public void init() {
        cache = loadFromIndex();
        if (searchService != null) {
            searchService.rebuild(cache);
        }
        LOG.info("IndexService initialised with " + cache.size() + " artifacts");
    }

    /**
     * Returns all cached artifact metadata. Thread-safe for concurrent reads.
     */
    public List<ArtifactMetadata> getAll() {
        lock.readLock().lock();
        try {
            return cache;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Re-reads INDEX.yaml and updates the in-memory cache.
     * Called externally after each repository commit.
     */
    public void refresh() {
        List<ArtifactMetadata> newData = loadFromIndex();
        lock.writeLock().lock();
        try {
            cache = newData;
        } finally {
            lock.writeLock().unlock();
        }
        if (searchService != null) {
            searchService.rebuild(newData);
        }
        LOG.fine("IndexService refreshed with " + newData.size() + " artifacts");
    }

    private List<ArtifactMetadata> loadFromIndex() {
        try {
            return List.copyOf(repository.list(null));
        } catch (Exception e) {
            LOG.warning("Failed to load index: " + e.getMessage());
            return List.of();
        }
    }
}
