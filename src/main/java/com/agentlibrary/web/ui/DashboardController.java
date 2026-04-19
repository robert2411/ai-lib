package com.agentlibrary.web.ui;

import com.agentlibrary.index.IndexService;
import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.model.ArtifactType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serves the dashboard (home page) showing recent artifacts, type counts, and quick search.
 */
@Controller
public class DashboardController {

    private final IndexService indexService;

    public DashboardController(IndexService indexService) {
        this.indexService = indexService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<ArtifactMetadata> all = indexService.getAll();

        // Recent artifacts: sorted by updated (or created) descending, limit 10
        List<ArtifactMetadata> recent = all.stream()
                .sorted(Comparator.comparing(
                        (ArtifactMetadata m) -> m.updated() != null ? m.updated() : m.created(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        // Type counts
        Map<ArtifactType, Long> typeCounts = all.stream()
                .collect(Collectors.groupingBy(ArtifactMetadata::type, Collectors.counting()));

        model.addAttribute("recentArtifacts", recent);
        model.addAttribute("typeCounts", typeCounts);
        model.addAttribute("totalCount", (long) all.size());

        return "dashboard";
    }
}
