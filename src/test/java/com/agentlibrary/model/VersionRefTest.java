package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class VersionRefTest {

    @Test
    void validConstruction() {
        Instant now = Instant.now();
        VersionRef ref = new VersionRef("1.0.0", "abc123", now);
        assertEquals("1.0.0", ref.version());
        assertEquals("abc123", ref.commitId());
        assertEquals(now, ref.timestamp());
    }

    @Test
    void nullVersionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new VersionRef(null, "abc123", Instant.now()));
    }

    @Test
    void blankVersionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new VersionRef("  ", "abc123", Instant.now()));
    }

    @Test
    void blankCommitIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new VersionRef("1.0.0", "  ", Instant.now()));
    }

    @Test
    void nullCommitIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new VersionRef("1.0.0", null, Instant.now()));
    }

    @Test
    void nullTimestampThrows() {
        assertThrows(NullPointerException.class,
                () -> new VersionRef("1.0.0", "abc123", null));
    }

    @Test
    void equality() {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        VersionRef a = new VersionRef("1.0.0", "abc123", t);
        VersionRef b = new VersionRef("1.0.0", "abc123", t);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
