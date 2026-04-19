package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HarnessTest {

    @Test
    void allValuesPresent() {
        assertNotNull(Harness.CLAUDE);
        assertNotNull(Harness.COPILOT);
        assertEquals(2, Harness.values().length);
    }

    @Test
    void fromSlugRoundTrip() {
        for (Harness h : Harness.values()) {
            assertEquals(h, Harness.fromSlug(h.slug()));
        }
    }

    @Test
    void fromSlugInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Harness.fromSlug("bogus"));
    }

    @Test
    void slugValues() {
        assertEquals("claude", Harness.CLAUDE.slug());
        assertEquals("copilot", Harness.COPILOT.slug());
    }
}
