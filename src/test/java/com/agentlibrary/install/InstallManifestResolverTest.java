package com.agentlibrary.install;

import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.ResolvedGroupMember;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InstallManifestResolver. ArtifactService is mocked.
 */
@ExtendWith(MockitoExtension.class)
class InstallManifestResolverTest {

    @Mock
    private ArtifactService artifactService;

    private InstallManifestResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new InstallManifestResolver(artifactService);
    }

    private ArtifactMetadata metadata(String name, ArtifactType type) {
        return new ArtifactMetadata(
                name, name, type, "1.0.0",
                "desc", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null,
                Instant.now(), null, null, null, null
        );
    }

    private ArtifactMetadata metadataWithInstall(String name, ArtifactType type, String target) {
        return new ArtifactMetadata(
                name, name, type, "1.0.0",
                "desc", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null,
                Instant.now(), null, new InstallConfig(target, null, null), null, null
        );
    }

    private Artifact artifact(ArtifactMetadata meta) {
        return new Artifact(meta, "body");
    }

    // === AC#1: Correct target paths per ArtifactType x Harness ===

    @Test
    void resolve_skill_claude_correctPath() {
        ArtifactMetadata meta = metadata("git-helper", ArtifactType.SKILL);
        when(artifactService.get("git-helper", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("git-helper"), Harness.CLAUDE);

        assertEquals(1, manifest.entries().size());
        InstallEntry entry = manifest.entries().get(0);
        assertEquals("git-helper", entry.slug());
        assertEquals(ArtifactType.SKILL, entry.type());
        assertEquals("skill.md", entry.sourcePath());
        assertEquals("~/.claude/skills/git-helper/", entry.targetPath());
        assertNull(entry.role());
    }

    @Test
    void resolve_agentClaude_claude_correctPath() {
        ArtifactMetadata meta = metadata("code-reviewer", ArtifactType.AGENT_CLAUDE);
        when(artifactService.get("code-reviewer", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("code-reviewer"), Harness.CLAUDE);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals(".claude/agents/code-reviewer.md", entry.targetPath());
        assertEquals("agent.md", entry.sourcePath());
    }

    @Test
    void resolve_agentCopilot_copilot_correctPath() {
        ArtifactMetadata meta = metadata("my-agent", ArtifactType.AGENT_COPILOT);
        when(artifactService.get("my-agent", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("my-agent"), Harness.COPILOT);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals(".github/agents/my-agent.md", entry.targetPath());
        assertEquals("agent.md", entry.sourcePath());
    }

    @Test
    void resolve_mcp_claude_correctPath() {
        ArtifactMetadata meta = metadata("my-server", ArtifactType.MCP);
        when(artifactService.get("my-server", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("my-server"), Harness.CLAUDE);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals(".claude/settings.json#mcpServers.my-server", entry.targetPath());
        assertEquals("mcp.json", entry.sourcePath());
    }

    @Test
    void resolve_mcp_copilot_correctPath() {
        ArtifactMetadata meta = metadata("my-server", ArtifactType.MCP);
        when(artifactService.get("my-server", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("my-server"), Harness.COPILOT);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals(".vscode/mcp.json#servers.my-server", entry.targetPath());
    }

    @Test
    void resolve_withCustomInstallTarget_usesMetadataOverDefault() {
        ArtifactMetadata meta = metadataWithInstall("my-skill", ArtifactType.SKILL, "custom/path/{name}.md");
        when(artifactService.get("my-skill", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("my-skill"), Harness.CLAUDE);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals("custom/path/my-skill.md", entry.targetPath());
    }

    // === AC#2: Unknown/unsupported harness throws descriptive exception ===

    @Test
    void resolve_unknownHarness_fromSlug_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Harness.fromSlug("unknown-harness"));
    }

    @Test
    void resolve_unsupportedTypeCombination_throwsIllegalArgumentException() {
        // OPENCODE type is not supported on CLAUDE harness
        ArtifactMetadata meta = metadata("my-opencode", ArtifactType.OPENCODE);
        when(artifactService.get("my-opencode", "latest")).thenReturn(artifact(meta));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(List.of("my-opencode"), Harness.CLAUDE));

        assertTrue(ex.getMessage().contains("opencode"));
        assertTrue(ex.getMessage().contains("claude"));
    }

    // === AC#3: Manifest serialises to JSON for API response ===

    @Test
    void manifest_serialisesToJson() throws Exception {
        ArtifactMetadata meta = metadata("git-helper", ArtifactType.SKILL);
        when(artifactService.get("git-helper", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("git-helper"), Harness.CLAUDE);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(manifest);

        assertTrue(json.contains("\"entries\""));
        assertTrue(json.contains("\"slug\""));
        assertTrue(json.contains("\"git-helper\""));
        assertTrue(json.contains("\"sourcePath\""));
        assertTrue(json.contains("\"targetPath\""));
        assertTrue(json.contains("\"harness\""));

        // Verify it can be parsed back
        var tree = mapper.readTree(json);
        assertTrue(tree.has("entries"));
        assertTrue(tree.get("entries").isArray());
        assertEquals(1, tree.get("entries").size());
        assertEquals("git-helper", tree.get("entries").get(0).get("slug").asText());
        // Role should be null for non-group entries
        assertTrue(tree.get("entries").get(0).get("role").isNull());
    }

    @Test
    void manifest_groupEntries_serialiseRoleToJson() throws Exception {
        ArtifactMetadata groupMeta = new ArtifactMetadata(
                "my-team", "My Team", ArtifactType.AGENT_GROUP, "1.0.0",
                "A team", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null,
                Instant.now(), null, null,
                List.of(new AgentGroupMember("mgr-agent", "manager")), null
        );
        when(artifactService.get("my-team", "latest")).thenReturn(artifact(groupMeta));

        ArtifactMetadata mgrMeta = metadata("mgr-agent", ArtifactType.AGENT_CLAUDE);
        when(artifactService.resolveGroup("my-team")).thenReturn(List.of(
                new ResolvedGroupMember("mgr-agent", "manager", mgrMeta)
        ));

        InstallManifest manifest = resolver.resolve(List.of("my-team"), Harness.CLAUDE);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(manifest);

        var tree = mapper.readTree(json);
        assertEquals("manager", tree.get("entries").get(0).get("role").asText());
    }

    // === AC#4: Agent-group slug expands to one entry per member ===

    @Test
    void resolve_agentGroup_expandsToMemberEntries() {
        // Set up group metadata
        ArtifactMetadata groupMeta = new ArtifactMetadata(
                "my-team", "My Team", ArtifactType.AGENT_GROUP, "1.0.0",
                "A team", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null,
                Instant.now(), null, null,
                List.of(
                        new AgentGroupMember("mgr-agent", "manager"),
                        new AgentGroupMember("impl-agent", "implementation"),
                        new AgentGroupMember("ana-agent", "analyse")
                ), null
        );
        when(artifactService.get("my-team", "latest")).thenReturn(artifact(groupMeta));

        // Set up resolved members (already sorted by role)
        ArtifactMetadata mgrMeta = metadata("mgr-agent", ArtifactType.AGENT_CLAUDE);
        ArtifactMetadata anaMeta = metadata("ana-agent", ArtifactType.AGENT_CLAUDE);
        ArtifactMetadata implMeta = metadata("impl-agent", ArtifactType.AGENT_CLAUDE);

        when(artifactService.resolveGroup("my-team")).thenReturn(List.of(
                new ResolvedGroupMember("mgr-agent", "manager", mgrMeta),
                new ResolvedGroupMember("ana-agent", "analyse", anaMeta),
                new ResolvedGroupMember("impl-agent", "implementation", implMeta)
        ));

        InstallManifest manifest = resolver.resolve(List.of("my-team"), Harness.CLAUDE);

        assertEquals(3, manifest.entries().size());
        assertEquals("mgr-agent", manifest.entries().get(0).slug());
        assertEquals("ana-agent", manifest.entries().get(1).slug());
        assertEquals("impl-agent", manifest.entries().get(2).slug());
        // Verify role field is populated for group members
        assertEquals("manager", manifest.entries().get(0).role());
        assertEquals("analyse", manifest.entries().get(1).role());
        assertEquals("implementation", manifest.entries().get(2).role());
    }

    // === AC#5: Expanded group entries ordered: manager, analyse, implementation, custom ===

    @Test
    void resolve_agentGroup_preservesRoleOrder() {
        ArtifactMetadata groupMeta = new ArtifactMetadata(
                "ordered-team", "Ordered Team", ArtifactType.AGENT_GROUP, "1.0.0",
                "Team", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null,
                Instant.now(), null, null,
                List.of(
                        new AgentGroupMember("custom-agent", "custom"),
                        new AgentGroupMember("impl-agent", "implementation"),
                        new AgentGroupMember("mgr-agent", "manager"),
                        new AgentGroupMember("ana-agent", "analyse")
                ), null
        );
        when(artifactService.get("ordered-team", "latest")).thenReturn(artifact(groupMeta));

        ArtifactMetadata mgrMeta = metadata("mgr-agent", ArtifactType.AGENT_CLAUDE);
        ArtifactMetadata anaMeta = metadata("ana-agent", ArtifactType.AGENT_CLAUDE);
        ArtifactMetadata implMeta = metadata("impl-agent", ArtifactType.AGENT_CLAUDE);
        ArtifactMetadata customMeta = metadata("custom-agent", ArtifactType.AGENT_CLAUDE);

        // resolveGroup already returns sorted by role order
        when(artifactService.resolveGroup("ordered-team")).thenReturn(List.of(
                new ResolvedGroupMember("mgr-agent", "manager", mgrMeta),
                new ResolvedGroupMember("ana-agent", "analyse", anaMeta),
                new ResolvedGroupMember("impl-agent", "implementation", implMeta),
                new ResolvedGroupMember("custom-agent", "custom", customMeta)
        ));

        InstallManifest manifest = resolver.resolve(List.of("ordered-team"), Harness.CLAUDE);

        assertEquals(4, manifest.entries().size());
        assertEquals("mgr-agent", manifest.entries().get(0).slug());
        assertEquals("ana-agent", manifest.entries().get(1).slug());
        assertEquals("impl-agent", manifest.entries().get(2).slug());
        assertEquals("custom-agent", manifest.entries().get(3).slug());
    }

    // === Edge cases ===

    @Test
    void resolve_emptySlugs_returnsEmptyManifest() {
        InstallManifest manifest = resolver.resolve(List.of(), Harness.CLAUDE);

        assertEquals(0, manifest.entries().size());
        assertEquals(Harness.CLAUDE, manifest.harness());
    }

    @Test
    void resolve_nullSlugs_returnsEmptyManifest() {
        InstallManifest manifest = resolver.resolve(null, Harness.CLAUDE);

        assertEquals(0, manifest.entries().size());
        assertEquals(Harness.CLAUDE, manifest.harness());
    }

    @Test
    void resolve_command_claude_correctPath() {
        ArtifactMetadata meta = metadata("my-cmd", ArtifactType.COMMAND);
        when(artifactService.get("my-cmd", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("my-cmd"), Harness.CLAUDE);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals("~/.claude/commands/my-cmd.md", entry.targetPath());
        assertEquals("command.md", entry.sourcePath());
    }

    @Test
    void resolve_hook_claude_correctPath() {
        ArtifactMetadata meta = metadata("pre-commit", ArtifactType.HOOK);
        when(artifactService.get("pre-commit", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("pre-commit"), Harness.CLAUDE);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals(".claude/settings.json#hooks.pre-commit", entry.targetPath());
        assertEquals("hook.json", entry.sourcePath());
    }

    @Test
    void resolve_claudeMd_claude_correctPath() {
        ArtifactMetadata meta = metadata("my-snippet", ArtifactType.CLAUDE_MD);
        when(artifactService.get("my-snippet", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("my-snippet"), Harness.CLAUDE);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals("CLAUDE.md", entry.targetPath());
        assertEquals("snippet.md", entry.sourcePath());
    }

    @Test
    void resolve_prompt_claude_correctPath() {
        ArtifactMetadata meta = metadata("my-prompt", ArtifactType.PROMPT);
        when(artifactService.get("my-prompt", "latest")).thenReturn(artifact(meta));

        InstallManifest manifest = resolver.resolve(List.of("my-prompt"), Harness.CLAUDE);

        InstallEntry entry = manifest.entries().get(0);
        assertEquals("~/.claude/prompts/my-prompt.md", entry.targetPath());
        assertEquals("prompt.md", entry.sourcePath());
    }

    @Test
    void resolve_multipleSlugs_returnsMultipleEntries() {
        ArtifactMetadata skillMeta = metadata("git-helper", ArtifactType.SKILL);
        ArtifactMetadata agentMeta = metadata("code-reviewer", ArtifactType.AGENT_CLAUDE);
        when(artifactService.get("git-helper", "latest")).thenReturn(artifact(skillMeta));
        when(artifactService.get("code-reviewer", "latest")).thenReturn(artifact(agentMeta));

        InstallManifest manifest = resolver.resolve(List.of("git-helper", "code-reviewer"), Harness.CLAUDE);

        assertEquals(2, manifest.entries().size());
        assertEquals("git-helper", manifest.entries().get(0).slug());
        assertEquals("code-reviewer", manifest.entries().get(1).slug());
    }
}
