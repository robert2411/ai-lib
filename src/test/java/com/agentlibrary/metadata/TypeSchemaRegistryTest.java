package com.agentlibrary.metadata;

import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Harness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeSchemaRegistryTest {

    @Test
    void schemaExistsForEachType() {
        for (ArtifactType type : ArtifactType.values()) {
            TypeSchema schema = TypeSchemaRegistry.forType(type);
            assertNotNull(schema, "Schema should exist for " + type);
            assertFalse(schema.requiredFields().isEmpty(),
                    "Schema should have at least one required field for " + type);
        }
    }

    @Test
    void skillSchemaRequiresDescription() {
        TypeSchema schema = TypeSchemaRegistry.forType(ArtifactType.SKILL);
        assertTrue(schema.requiredFields().contains("description"),
                "Skill schema should require 'description'");
    }

    @Test
    void agentGroupSchemaRequiresMembers() {
        TypeSchema schema = TypeSchemaRegistry.forType(ArtifactType.AGENT_GROUP);
        assertTrue(schema.requiredFields().contains("members"),
                "Agent-group schema should require 'members'");
    }

    @Test
    void agentClaudeSchemaAllowsOnlyClaude() {
        TypeSchema schema = TypeSchemaRegistry.forType(ArtifactType.AGENT_CLAUDE);
        assertFalse(schema.allowsAllHarnesses());
        assertTrue(schema.allowedHarnesses().contains(Harness.CLAUDE));
        assertFalse(schema.allowedHarnesses().contains(Harness.COPILOT));
    }

    @Test
    void agentGroupSchemaAllowsAllHarnesses() {
        TypeSchema schema = TypeSchemaRegistry.forType(ArtifactType.AGENT_GROUP);
        assertTrue(schema.allowsAllHarnesses());
    }

    @Test
    void allSchemasHaveNameTypeVersion() {
        for (ArtifactType type : ArtifactType.values()) {
            TypeSchema schema = TypeSchemaRegistry.forType(type);
            assertTrue(schema.requiredFields().contains("name"),
                    type + " schema should require 'name'");
            assertTrue(schema.requiredFields().contains("type"),
                    type + " schema should require 'type'");
            assertTrue(schema.requiredFields().contains("version"),
                    type + " schema should require 'version'");
        }
    }
}
