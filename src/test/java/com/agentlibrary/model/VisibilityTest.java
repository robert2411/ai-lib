package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VisibilityTest {

    @Test
    void allValuesPresent() {
        assertNotNull(Visibility.TEAM);
        assertNotNull(Visibility.PRIVATE);
        assertEquals(2, Visibility.values().length);
    }

    @Test
    void fromSlugRoundTrip() {
        for (Visibility v : Visibility.values()) {
            assertEquals(v, Visibility.fromSlug(v.slug()));
        }
    }

    @Test
    void fromSlugInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Visibility.fromSlug("bogus"));
    }

    @Test
    void slugValues() {
        assertEquals("team", Visibility.TEAM.slug());
        assertEquals("private", Visibility.PRIVATE.slug());
    }
}
