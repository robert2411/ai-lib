package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterTest {

    @Test
    void allNullsAllowed() {
        Filter filter = new Filter(null, null, null, null);
        assertNull(filter.type());
        assertNull(filter.harness());
        assertNull(filter.tag());
        assertNull(filter.query());
    }

    @Test
    void equality() {
        Filter a = new Filter(ArtifactType.SKILL, Harness.CLAUDE, "ai", "search");
        Filter b = new Filter(ArtifactType.SKILL, Harness.CLAUDE, "ai", "search");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequality() {
        Filter a = new Filter(ArtifactType.SKILL, null, null, null);
        Filter b = new Filter(ArtifactType.MCP, null, null, null);
        assertNotEquals(a, b);
    }
}
