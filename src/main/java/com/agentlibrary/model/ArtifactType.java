package com.agentlibrary.model;

/**
 * Enumerates all supported artifact types in the library.
 * Each value has a slug (used in YAML frontmatter) and a folder name (used in the git repo).
 */
public enum ArtifactType {

    SKILL("skill", "skills"),
    AGENT_CLAUDE("agent-claude", "agents-claude"),
    AGENT_COPILOT("agent-copilot", "agents-copilot"),
    AGENT_GROUP("agent-group", "agent-groups"),
    MCP("mcp", "mcp"),
    COMMAND("command", "commands"),
    HOOK("hook", "hooks"),
    CLAUDE_MD("claude-md", "claude-md"),
    OPENCODE("opencode", "opencode"),
    PROMPT("prompt", "prompts");

    private final String slug;
    private final String folderName;

    ArtifactType(String slug, String folderName) {
        this.slug = slug;
        this.folderName = folderName;
    }

    public String slug() {
        return slug;
    }

    public String folderName() {
        return folderName;
    }

    /**
     * Returns the ArtifactType matching the given slug.
     *
     * @param slug the slug to look up (e.g. "skill", "agent-group")
     * @return the matching ArtifactType
     * @throws IllegalArgumentException if no match is found
     */
    public static ArtifactType fromSlug(String slug) {
        for (ArtifactType type : values()) {
            if (type.slug.equals(slug)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ArtifactType slug: " + slug);
    }
}
