package com.agentlibrary.web.api;

import com.agentlibrary.install.BundleService;
import com.agentlibrary.install.InstallManifest;
import com.agentlibrary.install.InstallManifestResolver;
import com.agentlibrary.model.Harness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for install operations — manifest resolution and bundle download.
 */
@RestController
@RequestMapping("/api/v1/install")
public class InstallApiController {

    private final InstallManifestResolver manifestResolver;
    private final BundleService bundleService;

    public InstallApiController(InstallManifestResolver manifestResolver, BundleService bundleService) {
        this.manifestResolver = manifestResolver;
        this.bundleService = bundleService;
    }

    /**
     * Returns an install manifest with resolved target paths for the given slugs and harness.
     *
     * @param slugs   comma-separated list of artifact slugs
     * @param harness target harness (e.g. "claude", "copilot")
     * @return InstallManifest JSON
     */
    @GetMapping("/manifest")
    public ResponseEntity<InstallManifest> manifest(
            @RequestParam List<String> slugs,
            @RequestParam String harness) {
        Harness parsedHarness = Harness.fromSlug(harness);
        InstallManifest manifest = manifestResolver.resolve(slugs, parsedHarness);
        return ResponseEntity.ok(manifest);
    }

    /**
     * Returns a zip bundle containing all requested artifacts.
     *
     * @param slugs comma-separated list of artifact slugs
     * @return zip archive with Content-Disposition header
     */
    @GetMapping("/bundle")
    public ResponseEntity<byte[]> bundle(@RequestParam List<String> slugs) {
        byte[] zipBytes = bundleService.bundleAsZip(slugs);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"install-bundle.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }
}
