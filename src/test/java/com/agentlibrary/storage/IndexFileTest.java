package com.agentlibrary.storage;

import com.agentlibrary.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexFileTest {

    @Test
    void loadWithNullReturnsEmptyList() {
        List<ArtifactMetadata> result = IndexFile.load(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void loadWithEmptyStringReturnsEmptyList() {
        List<ArtifactMetadata> result = IndexFile.load("");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void loadWithBlankStringReturnsEmptyList() {
        List<ArtifactMetadata> result = IndexFile.load("   \n  ");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void loadWithValidYamlReturnsCorrectMetadataList() {
        String yaml = """
                artifacts:
                  - name: git-helper
                    type: skill
                    version: 1.2.0
                    title: Git Helper
                    description: A git helper skill
                    harnesses:
                      - claude
                      - copilot
                    tags:
                      - git
                      - workflow
                    category: developer-tools
                    author: robert
                    visibility: team
                    created: 2026-04-19T10:00:00Z
                    updated: 2026-04-19T10:00:00Z
                  - name: code-reviewer
                    type: agent-claude
                    version: 2.0.0
                    title: Code Reviewer
                    description: Reviews code
                    harnesses:
                      - claude
                    tags:
                      - review
                    author: alice
                    visibility: private
                    created: 2026-04-19T11:00:00Z
                    updated: 2026-04-19T11:00:00Z
                """;

        List<ArtifactMetadata> result = IndexFile.load(yaml);
        assertEquals(2, result.size());

        ArtifactMetadata first = result.get(0);
        assertEquals("git-helper", first.name());
        assertEquals(ArtifactType.SKILL, first.type());
        assertEquals("1.2.0", first.version());
        assertEquals("Git Helper", first.title());
        assertEquals(List.of(Harness.CLAUDE, Harness.COPILOT), first.harnesses());
        assertEquals(List.of("git", "workflow"), first.tags());
        assertEquals("developer-tools", first.category());
        assertEquals("robert", first.author());
        assertEquals(Visibility.TEAM, first.visibility());

        ArtifactMetadata second = result.get(1);
        assertEquals("code-reviewer", second.name());
        assertEquals(ArtifactType.AGENT_CLAUDE, second.type());
        assertEquals("2.0.0", second.version());
    }

    @Test
    void loadWithMissingArtifactsKeyReturnsEmptyList() {
        String yaml = """
                something_else:
                  - name: test
                """;
        List<ArtifactMetadata> result = IndexFile.load(yaml);
        assertTrue(result.isEmpty());
    }

    @Test
    void saveProducesValidYamlReadableByLoad() {
        List<ArtifactMetadata> entries = List.of(
                createMetadata("skill-a", ArtifactType.SKILL, "1.0.0"),
                createMetadata("agent-b", ArtifactType.AGENT_CLAUDE, "2.1.0")
        );

        String yaml = IndexFile.save(entries);
        assertNotNull(yaml);
        assertFalse(yaml.isBlank());

        List<ArtifactMetadata> reloaded = IndexFile.load(yaml);
        assertEquals(2, reloaded.size());
        assertEquals("skill-a", reloaded.get(0).name());
        assertEquals(ArtifactType.SKILL, reloaded.get(0).type());
        assertEquals("1.0.0", reloaded.get(0).version());
        assertEquals("agent-b", reloaded.get(1).name());
        assertEquals(ArtifactType.AGENT_CLAUDE, reloaded.get(1).type());
        assertEquals("2.1.0", reloaded.get(1).version());
    }

    @Test
    void saveWithEmptyListProducesValidYaml() {
        String yaml = IndexFile.save(List.of());
        assertNotNull(yaml);

        List<ArtifactMetadata> reloaded = IndexFile.load(yaml);
        assertTrue(reloaded.isEmpty());
    }

    @Test
    void orderingIsPreserved() {
        List<ArtifactMetadata> entries = List.of(
                createMetadata("charlie", ArtifactType.SKILL, "1.0.0"),
                createMetadata("alpha", ArtifactType.COMMAND, "1.0.0"),
                createMetadata("bravo", ArtifactType.MCP, "1.0.0")
        );

        String yaml = IndexFile.save(entries);
        List<ArtifactMetadata> reloaded = IndexFile.load(yaml);

        assertEquals(3, reloaded.size());
        assertEquals("charlie", reloaded.get(0).name());
        assertEquals("alpha", reloaded.get(1).name());
        assertEquals("bravo", reloaded.get(2).name());
    }

    @Test
    void allMetadataFieldsSurviveRoundTrip() {
        Instant now = Instant.parse("2026-04-19T10:00:00Z");
        ArtifactMetadata meta = new ArtifactMetadata(
                "full-test",
                "Full Test Artifact",
                ArtifactType.SKILL,
                "3.2.1",
                "A comprehensive test artifact",
                List.of(Harness.CLAUDE, Harness.COPILOT),
                List.of("test", "comprehensive"),
                "testing",
                "java",
                "tester",
                Visibility.TEAM,
                now,
                now,
                new InstallConfig(".claude", List.of("skill.md"), "append"),
                List.of(),
                null
        );

        String yaml = IndexFile.save(List.of(meta));
        List<ArtifactMetadata> reloaded = IndexFile.load(yaml);

        assertEquals(1, reloaded.size());
        ArtifactMetadata loaded = reloaded.get(0);
        assertEquals("full-test", loaded.name());
        assertEquals("Full Test Artifact", loaded.title());
        assertEquals(ArtifactType.SKILL, loaded.type());
        assertEquals("3.2.1", loaded.version());
        assertEquals("A comprehensive test artifact", loaded.description());
        assertEquals(List.of(Harness.CLAUDE, Harness.COPILOT), loaded.harnesses());
        assertEquals(List.of("test", "comprehensive"), loaded.tags());
        assertEquals("testing", loaded.category());
        assertEquals("java", loaded.language());
        assertEquals("tester", loaded.author());
        assertEquals(Visibility.TEAM, loaded.visibility());
        assertEquals(now, loaded.created());
        assertEquals(now, loaded.updated());
        assertNotNull(loaded.install());
        assertEquals(".claude", loaded.install().target());
        assertEquals(List.of("skill.md"), loaded.install().files());
        assertEquals("append", loaded.install().merge());
    }

    private ArtifactMetadata createMetadata(String name, ArtifactType type, String version) {
        return new ArtifactMetadata(
                name, name, type, version, "Description of " + name,
                List.of(Harness.CLAUDE), List.of("test"), "general",
                null, "author", Visibility.TEAM,
                Instant.parse("2026-04-19T10:00:00Z"),
                Instant.parse("2026-04-19T10:00:00Z"),
                null, null, null
        );
    }
}
