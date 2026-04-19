package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactMetadataTest {

    private ArtifactMetadata minimal() {
        return new ArtifactMetadata(
                "my-skill", null, ArtifactType.SKILL, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    void validConstruction() {
        Instant now = Instant.now();
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-skill", "My Skill", ArtifactType.SKILL, "1.2.3",
                "A skill", List.of(Harness.CLAUDE), List.of("ai", "tool"),
                "productivity", "java", "alice", Visibility.TEAM,
                now, now, new InstallConfig(".claude/", List.of("agent.md"), null),
                List.of(), Map.of("custom", "value")
        );
        assertEquals("my-skill", meta.name());
        assertEquals("My Skill", meta.title());
        assertEquals(ArtifactType.SKILL, meta.type());
        assertEquals("1.2.3", meta.version());
        assertEquals("A skill", meta.description());
        assertEquals(List.of(Harness.CLAUDE), meta.harnesses());
        assertEquals(List.of("ai", "tool"), meta.tags());
        assertEquals("productivity", meta.category());
        assertEquals("java", meta.language());
        assertEquals("alice", meta.author());
        assertEquals(Visibility.TEAM, meta.visibility());
        assertEquals(now, meta.created());
        assertEquals(now, meta.updated());
        assertNotNull(meta.install());
        assertEquals(List.of(), meta.members());
        assertEquals(Map.of("custom", "value"), meta.extra());
    }

    @Test
    void nullSlugThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArtifactMetadata(null, null, ArtifactType.SKILL, "1.0.0",
                        null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void blankSlugThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArtifactMetadata("  ", null, ArtifactType.SKILL, "1.0.0",
                        null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void nullTypeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArtifactMetadata("my-skill", null, null, "1.0.0",
                        null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void nullVersionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArtifactMetadata("my-skill", null, ArtifactType.SKILL, null,
                        null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void blankVersionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ArtifactMetadata("my-skill", null, ArtifactType.SKILL, "  ",
                        null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void harnessesDefensivelyCopied() {
        List<Harness> list = new ArrayList<>();
        list.add(Harness.CLAUDE);
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-skill", null, ArtifactType.SKILL, "1.0.0",
                null, list, null, null, null, null, null, null, null, null, null, null
        );
        list.add(Harness.COPILOT);
        assertEquals(1, meta.harnesses().size(), "Mutating source should not affect record");
    }

    @Test
    void tagsDefensivelyCopied() {
        List<String> list = new ArrayList<>();
        list.add("ai");
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-skill", null, ArtifactType.SKILL, "1.0.0",
                null, null, list, null, null, null, null, null, null, null, null, null
        );
        list.add("extra");
        assertEquals(1, meta.tags().size(), "Mutating source should not affect record");
    }

    @Test
    void membersDefensivelyCopied() {
        List<AgentGroupMember> list = new ArrayList<>();
        list.add(new AgentGroupMember("agent-a", "manager"));
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-group", null, ArtifactType.AGENT_GROUP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, list, null
        );
        list.add(new AgentGroupMember("agent-b", "custom"));
        assertEquals(1, meta.members().size(), "Mutating source should not affect record");
    }

    @Test
    void extraDefensivelyCopied() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-skill", null, ArtifactType.SKILL, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, map
        );
        map.put("extra", "nope");
        assertEquals(1, meta.extra().size(), "Mutating source should not affect record");
    }

    @Test
    void agentGroupWithMembers() {
        List<AgentGroupMember> members = List.of(
                new AgentGroupMember("manager-agent", "manager"),
                new AgentGroupMember("impl-agent", "implementation"),
                new AgentGroupMember("analyser", "analyse")
        );
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-group", null, ArtifactType.AGENT_GROUP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, members, null
        );
        assertEquals(3, meta.members().size());
        assertEquals("manager-agent", meta.members().get(0).slug());
        assertEquals("manager", meta.members().get(0).role());
    }

    @Test
    void equality() {
        ArtifactMetadata a = minimal();
        ArtifactMetadata b = minimal();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void nullOptionalFieldsDefaultToEmptyCollections() {
        ArtifactMetadata meta = minimal();
        assertNotNull(meta.harnesses());
        assertEquals(0, meta.harnesses().size());
        assertNotNull(meta.tags());
        assertEquals(0, meta.tags().size());
        assertNotNull(meta.members());
        assertEquals(0, meta.members().size());
        assertNotNull(meta.extra());
        assertEquals(0, meta.extra().size());
    }
}
