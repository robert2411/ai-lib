package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactTypeTest {

    @Test
    void allValuesHaveSlugs() {
        for (ArtifactType type : ArtifactType.values()) {
            assertNotNull(type.slug(), "slug should not be null for " + type);
            assertFalse(type.slug().isBlank(), "slug should not be blank for " + type);
        }
    }

    @Test
    void allValuesHaveFolderNames() {
        for (ArtifactType type : ArtifactType.values()) {
            assertNotNull(type.folderName(), "folderName should not be null for " + type);
            assertFalse(type.folderName().isBlank(), "folderName should not be blank for " + type);
        }
    }

    @Test
    void fromSlugRoundTrip() {
        for (ArtifactType type : ArtifactType.values()) {
            assertEquals(type, ArtifactType.fromSlug(type.slug()));
        }
    }

    @Test
    void fromSlugInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ArtifactType.fromSlug("bogus"));
    }

    @Test
    void agentGroupValuePresent() {
        assertNotNull(ArtifactType.AGENT_GROUP);
        assertEquals("agent-group", ArtifactType.AGENT_GROUP.slug());
    }

    @Test
    void valueCount() {
        assertEquals(10, ArtifactType.values().length);
    }
}
