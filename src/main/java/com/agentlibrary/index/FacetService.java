package com.agentlibrary.index;

import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Harness;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Provides distinct facet values (tags, types, harnesses, etc.) from the index
 * for use in filter dropdowns and faceted navigation.
 */
@Service
public class FacetService {

    private final IndexService indexService;

    public FacetService(IndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * Returns all distinct tags across all indexed artifacts, sorted alphabetically.
     */
    public List<String> getDistinctTags() {
        return indexService.getAll().stream()
                .flatMap(m -> m.tags().stream())
                .collect(Collectors.toCollection(TreeSet::new))
                .stream().toList();
    }

    /**
     * Returns all distinct artifact types present in the index.
     */
    public List<ArtifactType> getDistinctTypes() {
        return indexService.getAll().stream()
                .map(ArtifactMetadata::type)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Returns all distinct harnesses present in the index.
     */
    public List<Harness> getDistinctHarnesses() {
        return indexService.getAll().stream()
                .flatMap(m -> m.harnesses().stream())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Returns all distinct categories present in the index, sorted alphabetically.
     */
    public List<String> getDistinctCategories() {
        return indexService.getAll().stream()
                .map(ArtifactMetadata::category)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toCollection(TreeSet::new))
                .stream().toList();
    }

    /**
     * Returns all distinct authors present in the index, sorted alphabetically.
     */
    public List<String> getDistinctAuthors() {
        return indexService.getAll().stream()
                .map(ArtifactMetadata::author)
                .filter(a -> a != null && !a.isBlank())
                .collect(Collectors.toCollection(TreeSet::new))
                .stream().toList();
    }
}
