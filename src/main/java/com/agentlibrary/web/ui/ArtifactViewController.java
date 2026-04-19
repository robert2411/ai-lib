package com.agentlibrary.web.ui;

import com.agentlibrary.model.Artifact;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.VersionRef;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.ResolvedGroupMember;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Serves artifact detail pages with version navigation.
 */
@Controller
public class ArtifactViewController {

    private final ArtifactService artifactService;

    public ArtifactViewController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping("/artifacts/{slug}")
    public String artifactLatest(@PathVariable String slug) {
        Artifact artifact = artifactService.get(slug, "latest");
        return "redirect:/artifacts/" + slug + "/" + artifact.metadata().version();
    }

    @GetMapping("/artifacts/{slug}/{version}")
    public String artifactDetail(@PathVariable String slug,
                                 @PathVariable String version,
                                 Model model) {
        Artifact artifact = artifactService.get(slug, version);
        List<VersionRef> versions = artifactService.getVersions(slug);

        model.addAttribute("artifact", artifact);
        model.addAttribute("versions", versions);
        model.addAttribute("currentVersion", version);

        // Build install command
        String installCmd = "agent-lib install " + artifact.metadata().type().slug()
                + "/" + slug + "@" + version;
        model.addAttribute("installCommand", installCmd);

        // For agent-group type, resolve members
        if (artifact.metadata().type() == ArtifactType.AGENT_GROUP) {
            List<ResolvedGroupMember> members = artifactService.resolveGroup(slug);
            model.addAttribute("members", members);
            String groupInstallCmd = "agent-lib install agent-group/" + slug;
            model.addAttribute("groupInstallCommand", groupInstallCmd);
        }

        return "artifact";
    }

    @GetMapping("/artifacts/{slug}/{version}/content")
    public String artifactContent(@PathVariable String slug,
                                  @PathVariable String version,
                                  Model model) {
        Artifact artifact = artifactService.get(slug, version);
        model.addAttribute("artifact", artifact);
        model.addAttribute("currentVersion", version);

        if (artifact.metadata().type() == ArtifactType.AGENT_GROUP) {
            List<ResolvedGroupMember> members = artifactService.resolveGroup(slug);
            model.addAttribute("members", members);
        }

        return "fragments/artifact-content :: content";
    }

    @GetMapping("/artifacts/{slug}/{version}/raw")
    public ResponseEntity<String> artifactRaw(@PathVariable String slug,
                                              @PathVariable String version) {
        Artifact artifact = artifactService.get(slug, version);
        String filename = slug + "-" + version + ".yaml";

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(artifact.content());
    }
}
