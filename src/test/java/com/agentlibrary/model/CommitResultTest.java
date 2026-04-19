package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CommitResultTest {

    @Test
    void validConstruction() {
        Instant now = Instant.now();
        CommitResult result = new CommitResult("abc123", now, "Initial commit");
        assertEquals("abc123", result.commitId());
        assertEquals(now, result.timestamp());
        assertEquals("Initial commit", result.message());
    }

    @Test
    void nullCommitIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CommitResult(null, Instant.now(), "msg"));
    }

    @Test
    void blankCommitIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new CommitResult("  ", Instant.now(), "msg"));
    }

    @Test
    void nullTimestampAllowed() {
        // timestamp is optional for CommitResult
        CommitResult result = new CommitResult("abc123", null, "msg");
        assertNull(result.timestamp());
    }

    @Test
    void equality() {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        CommitResult a = new CommitResult("abc123", t, "msg");
        CommitResult b = new CommitResult("abc123", t, "msg");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
