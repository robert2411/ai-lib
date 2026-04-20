package com.agentlibrary.bootstrap;

import com.agentlibrary.index.IndexService;
import com.agentlibrary.model.User;
import com.agentlibrary.service.ArtifactService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Seeds the artifact repository on first startup when the repo is empty.
 * Runs after ApplicationReadyEvent to ensure IndexConfig's CommandLineRunner
 * (which wires the refresh callback) has already completed.
 *
 * Idempotent: skips seeding if the index already contains artifacts.
 */
@Component
public class Bootstrapper implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = Logger.getLogger(Bootstrapper.class.getName());

    private static final User SYSTEM_USER = new User("system", Set.of("ADMIN"));

    /**
     * Seed resource paths — ORDER MATTERS.
     * Individual artifacts must be seeded before agent-group so that
     * group member validation passes.
     */
    private static final List<String> SEED_RESOURCES = List.of(
            "seeds/agent-library-install.md",
            "seeds/agent-lib-sh.md",
            "seeds/agent-lib-common-sh.md",
            "seeds/agent-lib-readme.md",
            "seeds/dev-team-group.md"
    );

    private final ArtifactService artifactService;
    private final IndexService indexService;

    public Bootstrapper(ArtifactService artifactService, IndexService indexService) {
        this.artifactService = artifactService;
        this.indexService = indexService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!indexService.getAll().isEmpty()) {
            LOG.info("Repo already seeded, skipping bootstrap");
            return;
        }

        LOG.info("Empty repo detected — seeding initial artifacts...");
        seedAll();
    }

    /**
     * Seeds all artifacts from classpath resources.
     * Each seed is processed independently — one failure does not prevent others.
     */
    void seedAll() {
        int seeded = 0;
        int failed = 0;

        for (String resourcePath : SEED_RESOURCES) {
            try {
                String content = readResource(resourcePath);
                artifactService.createOrUpdate(content, SYSTEM_USER);
                LOG.info("Seeded artifact: " + resourcePath);
                seeded++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to seed " + resourcePath + ": " + e.getMessage()
                        + " — continuing with remaining seeds", e);
                failed++;
            }
        }

        LOG.info("Bootstrap complete: " + seeded + " seeded, " + failed + " failed");
    }

    private String readResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
