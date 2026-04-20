package com.agentlibrary.install;

import com.agentlibrary.model.ArtifactType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InstallEntry record, including the nullable role field.
 */
class InstallEntryTest {

    @Test
    void constructor_withRole_storesRoleField() {
        InstallEntry entry = new InstallEntry("my-agent", ArtifactType.AGENT_CLAUDE, "agent.md",
                ".claude/agents/my-agent.md", "manager");

        assertEquals("my-agent", entry.slug());
        assertEquals(ArtifactType.AGENT_CLAUDE, entry.type());
        assertEquals("agent.md", entry.sourcePath());
        assertEquals(".claude/agents/my-agent.md", entry.targetPath());
        assertEquals("manager", entry.role());
    }

    @Test
    void constructor_withNullRole_allowsNull() {
        InstallEntry entry = new InstallEntry("my-skill", ArtifactType.SKILL, "skill.md",
                "~/.claude/skills/my-skill/", null);

        assertNull(entry.role());
    }

    @Test
    void convenienceConstructor_setsRoleToNull() {
        InstallEntry entry = new InstallEntry("my-skill", ArtifactType.SKILL, "skill.md",
                "~/.claude/skills/my-skill/");

        assertNull(entry.role());
        assertEquals("my-skill", entry.slug());
        assertEquals(ArtifactType.SKILL, entry.type());
        assertEquals("skill.md", entry.sourcePath());
        assertEquals("~/.claude/skills/my-skill/", entry.targetPath());
    }

    @Test
    void constructor_nullSlug_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new InstallEntry(null, ArtifactType.SKILL, "skill.md", "path/"));
    }

    @Test
    void constructor_blankSlug_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new InstallEntry("  ", ArtifactType.SKILL, "skill.md", "path/"));
    }

    @Test
    void constructor_nullType_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new InstallEntry("slug", null, "skill.md", "path/"));
    }

    @Test
    void constructor_nullSourcePath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new InstallEntry("slug", ArtifactType.SKILL, null, "path/"));
    }

    @Test
    void constructor_nullTargetPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new InstallEntry("slug", ArtifactType.SKILL, "skill.md", null));
    }

    @Test
    void roleField_serialisesWithDifferentRoles() {
        InstallEntry manager = new InstallEntry("mgr", ArtifactType.AGENT_CLAUDE, "agent.md", "path/", "manager");
        InstallEntry analyse = new InstallEntry("ana", ArtifactType.AGENT_CLAUDE, "agent.md", "path/", "analyse");
        InstallEntry impl = new InstallEntry("impl", ArtifactType.AGENT_CLAUDE, "agent.md", "path/", "implementation");

        assertEquals("manager", manager.role());
        assertEquals("analyse", analyse.role());
        assertEquals("implementation", impl.role());
    }
}
