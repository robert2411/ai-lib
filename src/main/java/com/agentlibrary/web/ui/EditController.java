package com.agentlibrary.web.ui;

import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.metadata.ValidationException;
import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles artifact creation, editing, and deletion.
 * Requires EDITOR role (ADMIN inherits via role hierarchy).
 */
@Controller
@PreAuthorize("hasRole('EDITOR')")
public class EditController {

    private final ArtifactService artifactService;

    public EditController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping("/artifacts/new")
    public String newArtifact(Model model) {
        ArtifactForm form = new ArtifactForm();
        form.setVersion("1.0.0");
        form.setVisibility("team");

        model.addAttribute("form", form);
        model.addAttribute("isNew", true);
        model.addAttribute("pageTitle", "New Artifact");
        model.addAttribute("types", ArtifactType.values());
        model.addAttribute("allHarnesses", Harness.values());
        return "edit";
    }

    @GetMapping("/artifacts/{slug}/edit")
    public String editArtifact(@PathVariable String slug, Model model) {
        Artifact artifact = artifactService.get(slug, "latest");
        ArtifactMetadata meta = artifact.metadata();

        ArtifactForm form = new ArtifactForm();
        form.setName(meta.name());
        form.setTitle(meta.title());
        form.setType(meta.type().slug());
        form.setVersion(meta.version());
        form.setDescription(meta.description());
        form.setHarnesses(meta.harnesses().stream().map(Harness::slug).toList());
        form.setTags(String.join(", ", meta.tags()));
        form.setCategory(meta.category());
        form.setLanguage(meta.language());
        form.setAuthor(meta.author());
        form.setVisibility(meta.visibility() != null ? meta.visibility().name().toLowerCase() : "team");
        form.setContent(artifact.content());

        model.addAttribute("form", form);
        model.addAttribute("isNew", false);
        model.addAttribute("pageTitle", "Edit: " + (meta.title() != null ? meta.title() : meta.name()));
        model.addAttribute("types", ArtifactType.values());
        model.addAttribute("allHarnesses", Harness.values());
        return "edit";
    }

    @PostMapping("/artifacts/save")
    public String saveArtifact(@Valid @ModelAttribute("form") ArtifactForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes,
                               Principal principal) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isNew", form.getName() == null || form.getName().isBlank());
            model.addAttribute("pageTitle", "Fix Errors");
            model.addAttribute("types", ArtifactType.values());
            model.addAttribute("allHarnesses", Harness.values());
            return "edit";
        }

        try {
            Artifact artifact = buildArtifact(form);
            String rawContent = MetadataCodec.encode(artifact);
            User user = extractUser(principal);
            artifactService.createOrUpdate(rawContent, user);

            redirectAttributes.addFlashAttribute("successMessage", "Artifact saved successfully");
            return "redirect:/artifacts/" + form.getName() + "/" + form.getVersion();
        } catch (ValidationException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("isNew", false);
            model.addAttribute("pageTitle", "Fix Errors");
            model.addAttribute("types", ArtifactType.values());
            model.addAttribute("allHarnesses", Harness.values());
            return "edit";
        }
    }

    @PostMapping("/artifacts/{slug}/delete")
    public String deleteArtifact(@PathVariable String slug,
                                 RedirectAttributes redirectAttributes,
                                 Principal principal) {
        User user = extractUser(principal);
        artifactService.delete(slug, user);
        redirectAttributes.addFlashAttribute("successMessage", "Artifact deleted");
        return "redirect:/browse";
    }

    private Artifact buildArtifact(ArtifactForm form) {
        ArtifactType type = ArtifactType.fromSlug(form.getType());
        List<Harness> harnesses = form.getHarnesses() != null
                ? form.getHarnesses().stream().map(Harness::fromSlug).toList()
                : List.of();
        List<String> tags = form.getTags() != null && !form.getTags().isBlank()
                ? Arrays.stream(form.getTags().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
        Visibility visibility = form.getVisibility() != null && !form.getVisibility().isBlank()
                ? Visibility.fromSlug(form.getVisibility())
                : null;

        ArtifactMetadata metadata = new ArtifactMetadata(
                form.getName(),
                form.getTitle(),
                type,
                form.getVersion(),
                form.getDescription(),
                harnesses,
                tags,
                form.getCategory(),
                form.getLanguage(),
                form.getAuthor(),
                visibility,
                Instant.now(), // created
                Instant.now(), // updated
                null, // install config
                null, // members
                null  // extra
        );

        String content = form.getContent() != null ? form.getContent() : "";
        return new Artifact(metadata, content);
    }

    private User extractUser(Principal principal) {
        if (principal instanceof Authentication auth) {
            Set<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                    .collect(Collectors.toSet());
            return new User(auth.getName(), roles);
        }
        return new User(principal.getName(), Set.of("USER"));
    }
}
