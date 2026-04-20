package com.agentlibrary.bootstrap;

import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.model.Artifact;
import com.agentlibrary.model.ArtifactType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all seed resource files are valid and parseable.
 */
class SeedFilesValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "seeds/agent-library-install.md",
            "seeds/agent-lib-sh.md",
            "seeds/agent-lib-common-sh.md",
            "seeds/agent-lib-readme.md",
            "seeds/dev-team-group.md"
    })
    void seedFile_parseableByMetadataCodec(String path) throws IOException {
        String content = readResource(path);

        assertNotNull(content);
        assertTrue(content.startsWith("---"), "Seed file must start with frontmatter delimiter");

        Artifact artifact = MetadataCodec.decode(content);

        assertNotNull(artifact.metadata());
        assertNotNull(artifact.metadata().name());
        assertNotNull(artifact.metadata().type());
        assertNotNull(artifact.metadata().version());
        assertFalse(artifact.content().isBlank(), "Seed file must have body content");
    }

    @Test
    void agentGroupSeed_hasCorrectMembers() throws IOException {
        String content = readResource("seeds/dev-team-group.md");
        Artifact artifact = MetadataCodec.decode(content);

        assertEquals(ArtifactType.AGENT_GROUP, artifact.metadata().type());
        assertEquals("dev-team-group", artifact.metadata().name());
        assertEquals(3, artifact.metadata().members().size());

        // Verify members reference slugs that exist in other seed files
        var memberSlugs = artifact.metadata().members().stream()
                .map(m -> m.slug())
                .collect(Collectors.toSet());

        assertTrue(memberSlugs.contains("agent-lib-sh"));
        assertTrue(memberSlugs.contains("agent-lib-common-sh"));
        assertTrue(memberSlugs.contains("agent-lib-readme"));
    }

    @Test
    void agentGroupSeed_hasAllRoles() throws IOException {
        String content = readResource("seeds/dev-team-group.md");
        Artifact artifact = MetadataCodec.decode(content);

        var roles = artifact.metadata().members().stream()
                .map(m -> m.role())
                .collect(Collectors.toSet());

        assertTrue(roles.contains("manager"));
        assertTrue(roles.contains("analyse"));
        assertTrue(roles.contains("implementation"));
    }

    @Test
    void allSeedFiles_haveUniqueNames() throws IOException {
        String[] paths = {
                "seeds/agent-library-install.md",
                "seeds/agent-lib-sh.md",
                "seeds/agent-lib-common-sh.md",
                "seeds/agent-lib-readme.md",
                "seeds/dev-team-group.md"
        };

        var names = new java.util.HashSet<String>();
        for (String path : paths) {
            String content = readResource(path);
            Artifact artifact = MetadataCodec.decode(content);
            assertTrue(names.add(artifact.metadata().name()),
                    "Duplicate seed name: " + artifact.metadata().name());
        }

        assertEquals(5, names.size());
    }

    private String readResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(is, "Resource not found: " + path);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
