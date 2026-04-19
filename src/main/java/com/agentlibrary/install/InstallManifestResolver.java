package com.agentlibrary.install;

import com.agentlibrary.model.ArtifactMetadata;
import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Harness;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.ResolvedGroupMember;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves install target paths for artifacts based on their type and target harness.
 * Supports expanding agent-group slugs into their member entries.
 */
@Component
public class InstallManifestResolver {

    /**
     * Maps each Harness to its supported ArtifactType target path templates.
     * The placeholder {name} is substituted with the artifact's name.
     */
    private static final Map<Harness, Map<ArtifactType, String>> HARNESS_TARGET_PATHS = Map.of(
            Harness.CLAUDE, Map.of(
                    ArtifactType.SKILL, "~/.claude/skills/{name}/",
                    ArtifactType.AGENT_CLAUDE, ".claude/agents/{name}.md",
                    ArtifactType.MCP, ".claude/settings.json#mcpServers.{name}",
                    ArtifactType.COMMAND, "~/.claude/commands/{name}.md",
                    ArtifactType.HOOK, ".claude/settings.json#hooks.{name}",
                    ArtifactType.CLAUDE_MD, "CLAUDE.md",
                    ArtifactType.PROMPT, "~/.claude/prompts/{name}.md"
            ),
            Harness.COPILOT, Map.of(
                    ArtifactType.AGENT_COPILOT, ".github/agents/{name}.md",
                    ArtifactType.MCP, ".vscode/mcp.json#servers.{name}"
            )
    );

    /**
     * Maps each ArtifactType to its canonical primary file name within a bundle.
     */
    private static final Map<ArtifactType, String> PRIMARY_FILE_NAMES = Map.of(
            ArtifactType.SKILL, "skill.md",
            ArtifactType.AGENT_CLAUDE, "agent.md",
            ArtifactType.AGENT_COPILOT, "agent.md",
            ArtifactType.MCP, "mcp.json",
            ArtifactType.COMMAND, "command.md",
            ArtifactType.HOOK, "hook.json",
            ArtifactType.CLAUDE_MD, "snippet.md",
            ArtifactType.OPENCODE, "config.yaml",
            ArtifactType.PROMPT, "prompt.md"
    );

    private final ArtifactService artifactService;

    public InstallManifestResolver(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    /**
     * Resolves an install manifest for the given slugs and harness.
     * Agent-group slugs are expanded to one entry per member agent, ordered by role.
     *
     * @param slugs   list of artifact slugs to resolve
     * @param harness the target harness
     * @return the resolved install manifest
     * @throws IllegalArgumentException if harness has no mapping for a given type
     */
    public InstallManifest resolve(List<String> slugs, Harness harness) {
        if (slugs == null || slugs.isEmpty()) {
            return new InstallManifest(List.of(), harness);
        }

        List<InstallEntry> entries = new ArrayList<>();

        for (String slug : slugs) {
            var artifact = artifactService.get(slug, "latest");
            ArtifactMetadata metadata = artifact.metadata();

            if (metadata.type() == ArtifactType.AGENT_GROUP) {
                // Expand group into member entries
                List<ResolvedGroupMember> members = artifactService.resolveGroup(slug);
                for (ResolvedGroupMember member : members) {
                    String targetPath = resolveTargetPath(member.metadata(), harness);
                    String sourcePath = primaryFileName(member.metadata().type());
                    entries.add(new InstallEntry(
                            member.slug(),
                            member.metadata().type(),
                            sourcePath,
                            targetPath
                    ));
                }
            } else {
                String targetPath = resolveTargetPath(metadata, harness);
                String sourcePath = primaryFileName(metadata.type());
                entries.add(new InstallEntry(slug, metadata.type(), sourcePath, targetPath));
            }
        }

        return new InstallManifest(entries, harness);
    }

    /**
     * Resolves the target path for an artifact on a specific harness.
     * Metadata install.target takes precedence over the harness map.
     */
    private String resolveTargetPath(ArtifactMetadata metadata, Harness harness) {
        // Check metadata override first
        if (metadata.install() != null && metadata.install().target() != null) {
            return metadata.install().target().replace("{name}", metadata.name());
        }

        // Fall back to harness map
        Map<ArtifactType, String> typePaths = HARNESS_TARGET_PATHS.get(harness);
        if (typePaths == null || !typePaths.containsKey(metadata.type())) {
            throw new IllegalArgumentException(
                    "No install path for " + metadata.type().slug() + " on harness " + harness.slug());
        }

        String template = typePaths.get(metadata.type());
        return template.replace("{name}", metadata.name());
    }

    /**
     * Returns the canonical primary file name for a given artifact type.
     */
    private static String primaryFileName(ArtifactType type) {
        String name = PRIMARY_FILE_NAMES.get(type);
        if (name == null) {
            throw new IllegalArgumentException("No primary file name for type: " + type.slug());
        }
        return name;
    }
}
