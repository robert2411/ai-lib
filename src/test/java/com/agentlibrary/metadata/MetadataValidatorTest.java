package com.agentlibrary.metadata;

import com.agentlibrary.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetadataValidatorTest {

    private ArtifactMetadata validSkill() {
        return new ArtifactMetadata(
                "my-skill", "My Skill", ArtifactType.SKILL, "1.0.0",
                "A useful skill", List.of(Harness.CLAUDE), List.of("ai"),
                null, null, null, null, null, null, null, null, null
        );
    }

    private ArtifactMetadata validAgentGroup() {
        return new ArtifactMetadata(
                "my-group", null, ArtifactType.AGENT_GROUP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null,
                List.of(
                        new AgentGroupMember("manager-agent", "manager"),
                        new AgentGroupMember("impl-agent", "implementation")
                ), null
        );
    }

    @Test
    void validFixtureForEachType() {
        // SKILL
        assertDoesNotThrow(() -> MetadataValidator.validate(validSkill()));

        // AGENT_CLAUDE
        assertDoesNotThrow(() -> MetadataValidator.validate(new ArtifactMetadata(
                "my-agent", null, ArtifactType.AGENT_CLAUDE, "1.0.0",
                null, List.of(Harness.CLAUDE), null, null, null, null, null, null, null, null, null, null
        )));

        // AGENT_COPILOT
        assertDoesNotThrow(() -> MetadataValidator.validate(new ArtifactMetadata(
                "my-agent", null, ArtifactType.AGENT_COPILOT, "1.0.0",
                null, List.of(Harness.COPILOT), null, null, null, null, null, null, null, null, null, null
        )));

        // AGENT_GROUP
        assertDoesNotThrow(() -> MetadataValidator.validate(validAgentGroup()));

        // MCP
        assertDoesNotThrow(() -> MetadataValidator.validate(new ArtifactMetadata(
                "my-mcp", null, ArtifactType.MCP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        )));

        // COMMAND
        assertDoesNotThrow(() -> MetadataValidator.validate(new ArtifactMetadata(
                "my-command", null, ArtifactType.COMMAND, "1.0.0",
                null, List.of(Harness.CLAUDE), null, null, null, null, null, null, null, null, null, null
        )));

        // HOOK
        assertDoesNotThrow(() -> MetadataValidator.validate(new ArtifactMetadata(
                "my-hook", null, ArtifactType.HOOK, "1.0.0",
                null, List.of(Harness.CLAUDE), null, null, null, null, null, null, null, null, null, null
        )));

        // CLAUDE_MD
        assertDoesNotThrow(() -> MetadataValidator.validate(new ArtifactMetadata(
                "my-claude-md", null, ArtifactType.CLAUDE_MD, "1.0.0",
                null, List.of(Harness.CLAUDE), null, null, null, null, null, null, null, null, null, null
        )));

        // OPENCODE
        assertDoesNotThrow(() -> MetadataValidator.validate(new ArtifactMetadata(
                "my-opencode", null, ArtifactType.OPENCODE, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        )));

        // PROMPT
        assertDoesNotThrow(() -> MetadataValidator.validate(new ArtifactMetadata(
                "my-prompt", null, ArtifactType.PROMPT, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        )));
    }

    @Test
    void missingRequiredFieldReturnsDescriptiveError() {
        // Skill requires 'description' but we pass null
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-skill", null, ArtifactType.SKILL, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        );
        ValidationException ex = assertThrows(ValidationException.class,
                () -> MetadataValidator.validate(meta));
        assertTrue(ex.getMessage().contains("description"),
                "Error should mention 'description': " + ex.getMessage());
    }

    @Test
    void invalidSlugRejected() {
        // Uppercase and space
        assertSlugRejected("My Skill");
        // Leading hyphen
        assertSlugRejected("-leading-hyphen");
        // Trailing hyphen
        assertSlugRejected("trailing-");
        // Double hyphen
        assertSlugRejected("double--hyphen");
        // Empty — can't construct ArtifactMetadata with blank, so test with numeric start
        assertSlugRejected("1invalid");
    }

    @Test
    void validSlugAccepted() {
        assertSlugAccepted("my-skill");
        assertSlugAccepted("a");
        assertSlugAccepted("skill-v2");
        assertSlugAccepted("my-long-artifact-name");
    }

    @Test
    void invalidSemverRejected() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-skill", null, ArtifactType.SKILL, "not-a-version",
                "desc", null, null, null, null, null, null, null, null, null, null, null
        );
        ValidationException ex = assertThrows(ValidationException.class,
                () -> MetadataValidator.validate(meta));
        assertTrue(ex.getMessage().contains("version"),
                "Error should mention 'version': " + ex.getMessage());

        // Missing patch
        ArtifactMetadata meta2 = new ArtifactMetadata(
                "my-skill", null, ArtifactType.SKILL, "1.0",
                "desc", null, null, null, null, null, null, null, null, null, null, null
        );
        assertThrows(ValidationException.class, () -> MetadataValidator.validate(meta2));
    }

    @Test
    void agentGroupRequiresMembers() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-group", null, ArtifactType.AGENT_GROUP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null,
                List.of(), null
        );
        ValidationException ex = assertThrows(ValidationException.class,
                () -> MetadataValidator.validate(meta));
        assertTrue(ex.getMessage().contains("member"),
                "Error should mention 'member': " + ex.getMessage());
    }

    @Test
    void agentGroupRequiresRecognisedRole() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-group", null, ArtifactType.AGENT_GROUP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null,
                List.of(new AgentGroupMember("agent-a", "unknown")), null
        );
        ValidationException ex = assertThrows(ValidationException.class,
                () -> MetadataValidator.validate(meta));
        assertTrue(ex.getMessage().contains("recognised role") || ex.getMessage().contains("unrecognised"),
                "Error should mention role issue: " + ex.getMessage());
    }

    @Test
    void agentGroupDuplicateSlugsRejected() {
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-group", null, ArtifactType.AGENT_GROUP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null,
                List.of(
                        new AgentGroupMember("same-agent", "manager"),
                        new AgentGroupMember("same-agent", "implementation")
                ), null
        );
        ValidationException ex = assertThrows(ValidationException.class,
                () -> MetadataValidator.validate(meta));
        assertTrue(ex.getMessage().contains("duplicate"),
                "Error should mention 'duplicate': " + ex.getMessage());
    }

    @Test
    void harnessValidation() {
        // agent-claude with COPILOT harness should be rejected
        ArtifactMetadata meta = new ArtifactMetadata(
                "my-agent", null, ArtifactType.AGENT_CLAUDE, "1.0.0",
                null, List.of(Harness.COPILOT), null, null, null, null, null, null, null, null, null, null
        );
        ValidationException ex = assertThrows(ValidationException.class,
                () -> MetadataValidator.validate(meta));
        assertTrue(ex.getMessage().contains("copilot") || ex.getMessage().contains("Harness"),
                "Error should mention harness issue: " + ex.getMessage());
    }

    @Test
    void multipleErrorsCollected() {
        // Invalid slug AND invalid semver
        ArtifactMetadata meta = new ArtifactMetadata(
                "BAD SLUG", null, ArtifactType.SKILL, "not-semver",
                "desc", null, null, null, null, null, null, null, null, null, null, null
        );
        ValidationException ex = assertThrows(ValidationException.class,
                () -> MetadataValidator.validate(meta));
        assertTrue(ex.errors().size() >= 2,
                "Should collect multiple errors: " + ex.errors());
    }

    // --- Helpers ---

    private void assertSlugRejected(String slug) {
        ArtifactMetadata meta = new ArtifactMetadata(
                slug, null, ArtifactType.MCP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        );
        assertThrows(ValidationException.class, () -> MetadataValidator.validate(meta),
                "Slug should be rejected: " + slug);
    }

    private void assertSlugAccepted(String slug) {
        ArtifactMetadata meta = new ArtifactMetadata(
                slug, null, ArtifactType.MCP, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        );
        assertDoesNotThrow(() -> MetadataValidator.validate(meta),
                "Slug should be accepted: " + slug);
    }
}
