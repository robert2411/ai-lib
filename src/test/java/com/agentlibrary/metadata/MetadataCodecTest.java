package com.agentlibrary.metadata;

import com.agentlibrary.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetadataCodecTest {

    @Test
    void decodeEncodeRoundTrip() {
        ArtifactMetadata original = new ArtifactMetadata(
                "my-skill", "My Skill", ArtifactType.SKILL, "1.0.0",
                "A useful skill", List.of(Harness.CLAUDE), List.of("ai", "tool"),
                "productivity", "java", "alice", Visibility.TEAM,
                null, null, null, List.of(), null
        );
        Artifact artifact = new Artifact(original, "# Body\nSome content here.\n");

        String encoded = MetadataCodec.encode(artifact);
        Artifact decoded = MetadataCodec.decode(encoded);

        assertEquals(original.name(), decoded.metadata().name());
        assertEquals(original.title(), decoded.metadata().title());
        assertEquals(original.type(), decoded.metadata().type());
        assertEquals(original.version(), decoded.metadata().version());
        assertEquals(original.description(), decoded.metadata().description());
        assertEquals(original.harnesses(), decoded.metadata().harnesses());
        assertEquals(original.tags(), decoded.metadata().tags());
        assertEquals(original.category(), decoded.metadata().category());
        assertEquals(original.language(), decoded.metadata().language());
        assertEquals(original.author(), decoded.metadata().author());
        assertEquals(original.visibility(), decoded.metadata().visibility());
    }

    @Test
    void bodyPreservedUnchanged() {
        String body = "# My Content\n\nWith multiple paragraphs.\n\n```java\nSystem.out.println(\"hello\");\n```\n";
        ArtifactMetadata meta = new ArtifactMetadata(
                "test", null, ArtifactType.SKILL, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        );
        Artifact artifact = new Artifact(meta, body);
        String encoded = MetadataCodec.encode(artifact);
        Artifact decoded = MetadataCodec.decode(encoded);
        assertEquals(body, decoded.content());
    }

    @Test
    void missingOptionalFields() {
        String raw = """
                ---
                name: minimal
                type: skill
                version: 1.0.0
                ---
                Body content
                """;
        Artifact artifact = MetadataCodec.decode(raw);
        assertEquals("minimal", artifact.metadata().name());
        assertEquals(ArtifactType.SKILL, artifact.metadata().type());
        assertEquals("1.0.0", artifact.metadata().version());
        assertNull(artifact.metadata().title());
        assertNull(artifact.metadata().description());
        assertNull(artifact.metadata().author());
        assertNull(artifact.metadata().visibility());
        assertTrue(artifact.metadata().harnesses().isEmpty());
        assertTrue(artifact.metadata().tags().isEmpty());
        assertTrue(artifact.metadata().members().isEmpty());
    }

    @Test
    void agentGroupMembersRoundTrip() {
        List<AgentGroupMember> members = List.of(
                new AgentGroupMember("manager-agent", "manager"),
                new AgentGroupMember("impl-agent", "implementation"),
                new AgentGroupMember("qa-agent", "custom")
        );
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-group", null, ArtifactType.AGENT_GROUP, "2.0.0",
                null, null, null, null, null, null, null, null, null, null, members, null
        );
        Artifact original = new Artifact(meta, "");
        String encoded = MetadataCodec.encode(original);
        Artifact decoded = MetadataCodec.decode(encoded);

        assertEquals(3, decoded.metadata().members().size());
        assertEquals("manager-agent", decoded.metadata().members().get(0).slug());
        assertEquals("manager", decoded.metadata().members().get(0).role());
        assertEquals("impl-agent", decoded.metadata().members().get(1).slug());
        assertEquals("implementation", decoded.metadata().members().get(1).role());
        assertEquals("qa-agent", decoded.metadata().members().get(2).slug());
        assertEquals("custom", decoded.metadata().members().get(2).role());
    }

    @Test
    void noFrontmatterThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MetadataCodec.decode("No frontmatter here"));
    }

    @Test
    void extraFieldsPreserved() {
        String raw = """
                ---
                name: my-skill
                type: skill
                version: 1.0.0
                custom_field: custom_value
                another: 42
                ---
                body
                """;
        Artifact artifact = MetadataCodec.decode(raw);
        assertEquals("custom_value", artifact.metadata().extra().get("custom_field"));
        assertEquals(42, artifact.metadata().extra().get("another"));
    }

    @Test
    void encodeOmitsNullFields() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "test", null, ArtifactType.SKILL, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        );
        Artifact artifact = new Artifact(meta, "");
        String encoded = MetadataCodec.encode(artifact);
        assertFalse(encoded.contains("title:"));
        assertFalse(encoded.contains("description:"));
        assertFalse(encoded.contains("author:"));
        assertFalse(encoded.contains("visibility:"));
    }

    @Test
    void encodeOmitsEmptyLists() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "test", null, ArtifactType.SKILL, "1.0.0",
                null, List.of(), List.of(), null, null, null, null, null, null, null, List.of(), null
        );
        Artifact artifact = new Artifact(meta, "");
        String encoded = MetadataCodec.encode(artifact);
        assertFalse(encoded.contains("harnesses:"));
        assertFalse(encoded.contains("tags:"));
        assertFalse(encoded.contains("members:"));
    }

    @Test
    void instantParsing() {
        Instant created = Instant.parse("2024-06-15T10:30:00Z");
        Instant updated = Instant.parse("2024-06-16T12:00:00Z");
        ArtifactMetadata meta = new ArtifactMetadata(
                "test", null, ArtifactType.SKILL, "1.0.0",
                null, null, null, null, null, null, null, created, updated, null, null, null
        );
        Artifact artifact = new Artifact(meta, "");
        String encoded = MetadataCodec.encode(artifact);
        Artifact decoded = MetadataCodec.decode(encoded);
        assertEquals(created, decoded.metadata().created());
        assertEquals(updated, decoded.metadata().updated());
    }

    @Test
    void harnessListParsing() {
        String raw = """
                ---
                name: test
                type: skill
                version: 1.0.0
                harnesses:
                  - claude
                  - copilot
                ---
                """;
        Artifact artifact = MetadataCodec.decode(raw);
        assertEquals(List.of(Harness.CLAUDE, Harness.COPILOT), artifact.metadata().harnesses());
    }

    @Test
    void installConfigParsing() {
        String raw = """
                ---
                name: test
                type: skill
                version: 1.0.0
                install:
                  target: .claude/
                  files:
                    - agent.md
                    - config.yml
                  merge: append
                ---
                """;
        Artifact artifact = MetadataCodec.decode(raw);
        assertNotNull(artifact.metadata().install());
        assertEquals(".claude/", artifact.metadata().install().target());
        assertEquals(List.of("agent.md", "config.yml"), artifact.metadata().install().files());
        assertEquals("append", artifact.metadata().install().merge());
    }

    @Test
    void hasFrontmatterTrue() {
        assertTrue(MetadataCodec.hasFrontmatter("---\nname: test\n---\nbody"));
    }

    @Test
    void hasFrontmatterFalse() {
        assertFalse(MetadataCodec.hasFrontmatter("no frontmatter"));
        assertFalse(MetadataCodec.hasFrontmatter(null));
    }

    @Test
    void extractBody() {
        String raw = "---\nname: test\ntype: skill\nversion: 1.0.0\n---\nBody here\n";
        assertEquals("Body here\n", MetadataCodec.extractBody(raw));
    }

    @Test
    void extractBodyNoFrontmatter() {
        String raw = "Just plain content";
        assertEquals(raw, MetadataCodec.extractBody(raw));
    }
}
