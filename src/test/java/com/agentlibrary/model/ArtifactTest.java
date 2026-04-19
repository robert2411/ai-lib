package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactTest {

    private ArtifactMetadata sampleMetadata() {
        return new ArtifactMetadata(
                "my-skill", null, ArtifactType.SKILL, "1.0.0",
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    void roundTrip() {
        ArtifactMetadata meta = sampleMetadata();
        Artifact artifact = new Artifact(meta, "# Hello World\nSome content here.");
        assertSame(meta, artifact.metadata());
        assertEquals("# Hello World\nSome content here.", artifact.content());
    }

    @Test
    void nullMetadataThrows() {
        assertThrows(NullPointerException.class, () -> new Artifact(null, "content"));
    }

    @Test
    void nullContentThrows() {
        assertThrows(NullPointerException.class, () -> new Artifact(sampleMetadata(), null));
    }

    @Test
    void emptyContentAllowed() {
        Artifact artifact = new Artifact(sampleMetadata(), "");
        assertEquals("", artifact.content());
    }

    @Test
    void equality() {
        ArtifactMetadata meta = sampleMetadata();
        Artifact a = new Artifact(meta, "content");
        Artifact b = new Artifact(meta, "content");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
