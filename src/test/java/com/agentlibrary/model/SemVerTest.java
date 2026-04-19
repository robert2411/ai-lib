package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemVerTest {

    @Test
    void parseSimple() {
        SemVer v = SemVer.parse("1.0.0");
        assertEquals(1, v.major());
        assertEquals(0, v.minor());
        assertEquals(0, v.patch());
        assertNull(v.prerelease());
    }

    @Test
    void parseWithPrerelease() {
        SemVer v = SemVer.parse("1.0.0-alpha.1");
        assertEquals(1, v.major());
        assertEquals(0, v.minor());
        assertEquals(0, v.patch());
        assertEquals("alpha.1", v.prerelease());
    }

    @Test
    void ordering() {
        SemVer v1 = SemVer.parse("1.0.0");
        SemVer v2 = SemVer.parse("1.2.0");
        SemVer v3 = SemVer.parse("2.0.0");
        assertTrue(v1.compareTo(v2) < 0, "1.0.0 < 1.2.0");
        assertTrue(v2.compareTo(v3) < 0, "1.2.0 < 2.0.0");
        assertTrue(v1.compareTo(v3) < 0, "1.0.0 < 2.0.0");
    }

    @Test
    void orderingMinor() {
        SemVer v1 = SemVer.parse("1.0.0");
        SemVer v2 = SemVer.parse("1.1.0");
        SemVer v3 = SemVer.parse("1.2.0");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v3) < 0);
    }

    @Test
    void orderingPatch() {
        SemVer v1 = SemVer.parse("1.0.0");
        SemVer v2 = SemVer.parse("1.0.1");
        SemVer v3 = SemVer.parse("1.0.2");
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v3) < 0);
    }

    @Test
    void prereleaseOrdering() {
        // Per semver 2.0.0 spec example:
        // 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta <
        // 1.0.0-beta.2 < 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0
        SemVer[] versions = {
                SemVer.parse("1.0.0-alpha"),
                SemVer.parse("1.0.0-alpha.1"),
                SemVer.parse("1.0.0-alpha.beta"),
                SemVer.parse("1.0.0-beta"),
                SemVer.parse("1.0.0-beta.2"),
                SemVer.parse("1.0.0-beta.11"),
                SemVer.parse("1.0.0-rc.1"),
                SemVer.parse("1.0.0")
        };

        for (int i = 0; i < versions.length - 1; i++) {
            assertTrue(versions[i].compareTo(versions[i + 1]) < 0,
                    versions[i] + " should be < " + versions[i + 1]);
        }
    }

    @Test
    void prereleaseHasLowerPrecedence() {
        SemVer alpha = SemVer.parse("1.0.0-alpha");
        SemVer release = SemVer.parse("1.0.0");
        assertTrue(alpha.compareTo(release) < 0, "1.0.0-alpha < 1.0.0");
        assertTrue(release.compareTo(alpha) > 0, "1.0.0 > 1.0.0-alpha");
    }

    @Test
    void invalidStrings() {
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse(""));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("not.a.version"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.0.0.0"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("01.0.0"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.0.0-"));
    }

    @Test
    void nullThrows() {
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse(null));
    }

    @Test
    void isValidTrue() {
        assertTrue(SemVer.isValid("1.2.3"));
        assertTrue(SemVer.isValid("0.0.1"));
        assertTrue(SemVer.isValid("1.0.0-beta.1"));
    }

    @Test
    void isValidFalse() {
        assertFalse(SemVer.isValid("garbage"));
        assertFalse(SemVer.isValid("1.0"));
        assertFalse(SemVer.isValid(null));
        assertFalse(SemVer.isValid(""));
    }

    @Test
    void toStringSimple() {
        assertEquals("2.1.0", SemVer.parse("2.1.0").toString());
    }

    @Test
    void toStringPrerelease() {
        assertEquals("1.0.0-beta.1", SemVer.parse("1.0.0-beta.1").toString());
    }

    @Test
    void equalityAndHashCode() {
        SemVer a = SemVer.parse("1.0.0");
        SemVer b = SemVer.parse("1.0.0");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        SemVer c = SemVer.parse("1.0.1");
        assertNotEquals(a, c);
    }

    @Test
    void compareToEqual() {
        assertEquals(0, SemVer.parse("1.0.0").compareTo(SemVer.parse("1.0.0")));
        assertEquals(0, SemVer.parse("1.0.0-alpha").compareTo(SemVer.parse("1.0.0-alpha")));
    }

    @Test
    void buildMetadataAccepted() {
        // Per semver 2.0.0, build metadata after + is valid
        SemVer v = SemVer.parse("1.0.0+build.1");
        assertEquals(1, v.major());
        assertEquals(0, v.minor());
        assertEquals(0, v.patch());
        assertNull(v.prerelease());
    }

    @Test
    void buildMetadataWithPrerelease() {
        SemVer v = SemVer.parse("1.0.0-alpha+build.123");
        assertEquals(1, v.major());
        assertEquals("alpha", v.prerelease());
    }

    @Test
    void buildMetadataIgnoredInComparison() {
        // Build metadata MUST be ignored in version precedence
        assertEquals(0, SemVer.parse("1.0.0+build.1").compareTo(SemVer.parse("1.0.0+build.2")));
        assertEquals(0, SemVer.parse("1.0.0+build.1").compareTo(SemVer.parse("1.0.0")));
    }

    @Test
    void buildMetadataIsValid() {
        assertTrue(SemVer.isValid("1.0.0+build.1"));
        assertTrue(SemVer.isValid("1.0.0-alpha+build"));
        assertTrue(SemVer.isValid("1.0.0+20230101"));
    }

    @Test
    void leadingZerosInPrereleaseRejected() {
        // Numeric prerelease identifiers MUST NOT have leading zeros
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.0.0-01"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.0.0-alpha.01"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.0.0-001"));
    }

    @Test
    void leadingZerosInPrereleaseNotValid() {
        assertFalse(SemVer.isValid("1.0.0-01"));
        assertFalse(SemVer.isValid("1.0.0-alpha.01"));
    }

    @Test
    void singleZeroPrereleaseIsValid() {
        // "0" alone is a valid numeric identifier (no leading zeros issue)
        SemVer v = SemVer.parse("1.0.0-0");
        assertEquals("0", v.prerelease());
        assertTrue(SemVer.isValid("1.0.0-0"));
    }
}
