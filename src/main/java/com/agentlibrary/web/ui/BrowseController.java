package com.agentlibrary.web.ui;

import com.agentlibrary.index.FacetService;
import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Filter;
import com.agentlibrary.model.Harness;
import com.agentlibrary.service.ArtifactService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Serves the browse page with filterable artifact listing and HTMX partial updates.
 */
@Controller
public class BrowseController {

    private final ArtifactService artifactService;
    private final FacetService facetService;

    public BrowseController(ArtifactService artifactService, FacetService facetService) {
        this.artifactService = artifactService;
        this.facetService = facetService;
    }

    @GetMapping("/browse")
    public String browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String harness,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        buildBrowseModel(q, type, harness, tag, page, size, model);

        // Add facets for the full page render
        model.addAttribute("types", facetService.getDistinctTypes());
        model.addAttribute("harnesses", facetService.getDistinctHarnesses());
        model.addAttribute("tags", facetService.getDistinctTags());

        return "browse";
    }

    @GetMapping("/browse/results")
    public String browseResults(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String harness,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model,
            HttpServletRequest request) {

        // Only serve fragment for HTMX requests
        if (request.getHeader("HX-Request") == null) {
            String redirect = buildRedirectUrl(q, type, harness, tag, page);
            return "redirect:" + redirect;
        }

        buildBrowseModel(q, type, harness, tag, page, size, model);
        return "fragments/browse-results :: results";
    }

    private void buildBrowseModel(String q, String type, String harness, String tag,
                                  int page, int size, Model model) {
        ArtifactType artifactType = (type != null && !type.isBlank())
                ? ArtifactType.fromSlug(type) : null;
        Harness harnessEnum = (harness != null && !harness.isBlank())
                ? Harness.fromSlug(harness) : null;
        String tagFilter = (tag != null && !tag.isBlank()) ? tag : null;

        Filter filter = new Filter(artifactType, harnessEnum, tagFilter, q);

        List<ArtifactMetadata> artifacts;
        long totalCount;

        if (q != null && !q.isBlank()) {
            artifacts = artifactService.search(q, filter);
            totalCount = artifacts.size();
            // Manual pagination for search results
            int fromIndex = Math.min(page * size, artifacts.size());
            int toIndex = Math.min(fromIndex + size, artifacts.size());
            artifacts = artifacts.subList(fromIndex, toIndex);
        } else {
            artifacts = artifactService.list(filter, page, size);
            totalCount = artifactService.count(filter);
        }

        int totalPages = (int) Math.ceil((double) totalCount / size);

        model.addAttribute("artifacts", artifacts);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("selectedType", type != null ? type : "");
        model.addAttribute("selectedHarness", harness != null ? harness : "");
        model.addAttribute("selectedTag", tag != null ? tag : "");
        model.addAttribute("query", q != null ? q : "");
        model.addAttribute("baseUrl", buildBaseUrl(q, type, harness, tag));
    }

    private String buildBaseUrl(String q, String type, String harness, String tag) {
        StringBuilder sb = new StringBuilder("/browse/results?");
        if (q != null && !q.isBlank()) sb.append("q=").append(q).append("&");
        if (type != null && !type.isBlank()) sb.append("type=").append(type).append("&");
        if (harness != null && !harness.isBlank()) sb.append("harness=").append(harness).append("&");
        if (tag != null && !tag.isBlank()) sb.append("tag=").append(tag).append("&");
        // Remove trailing &
        String url = sb.toString();
        if (url.endsWith("&")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private String buildRedirectUrl(String q, String type, String harness, String tag, int page) {
        StringBuilder sb = new StringBuilder("/browse?");
        if (q != null && !q.isBlank()) sb.append("q=").append(q).append("&");
        if (type != null && !type.isBlank()) sb.append("type=").append(type).append("&");
        if (harness != null && !harness.isBlank()) sb.append("harness=").append(harness).append("&");
        if (tag != null && !tag.isBlank()) sb.append("tag=").append(tag).append("&");
        if (page > 0) sb.append("page=").append(page).append("&");
        String url = sb.toString();
        if (url.endsWith("&") || url.endsWith("?")) url = url.substring(0, url.length() - 1);
        return url;
    }
}
