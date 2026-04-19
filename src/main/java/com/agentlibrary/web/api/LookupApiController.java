package com.agentlibrary.web.api;

import com.agentlibrary.index.FacetService;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Harness;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for lookup/facet endpoints.
 * Provides distinct values for tags, types, and harnesses.
 */
@RestController
@RequestMapping("/api/v1")
public class LookupApiController {

    private final FacetService facetService;

    public LookupApiController(FacetService facetService) {
        this.facetService = facetService;
    }

    /**
     * Returns all distinct tags across all indexed artifacts.
     */
    @GetMapping("/tags")
    public List<String> getTags() {
        return facetService.getDistinctTags();
    }

    /**
     * Returns all distinct artifact types present in the index.
     */
    @GetMapping("/types")
    public List<String> getTypes() {
        return facetService.getDistinctTypes().stream()
                .map(ArtifactType::slug)
                .toList();
    }

    /**
     * Returns all distinct harnesses present in the index.
     */
    @GetMapping("/harnesses")
    public List<String> getHarnesses() {
        return facetService.getDistinctHarnesses().stream()
                .map(Harness::slug)
                .toList();
    }
}
