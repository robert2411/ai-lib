package com.agentlibrary.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a Semantic Version (major.minor.patch-prerelease) per semver 2.0.0 spec.
 * Implements Comparable with ordering per the spec.
 */
public final class SemVer implements Comparable<SemVer> {

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([-a-zA-Z0-9]+(?:\\.[-a-zA-Z0-9]+)*))?$"
    );

    private final int major;
    private final int minor;
    private final int patch;
    private final String prerelease;

    private SemVer(int major, int minor, int patch, String prerelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String prerelease() {
        return prerelease;
    }

    /**
     * Parses a semver string into a SemVer instance.
     *
     * @param version the version string (e.g. "1.2.3" or "1.0.0-alpha.1")
     * @return the parsed SemVer
     * @throws IllegalArgumentException if the format is invalid
     */
    public static SemVer parse(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version string must not be null or blank");
        }
        Matcher matcher = SEMVER_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semver format: " + version);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        String prerelease = matcher.group(4); // nullable
        return new SemVer(major, minor, patch, prerelease);
    }

    /**
     * Returns true if the given string is a valid semver version.
     */
    public static boolean isValid(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        return SEMVER_PATTERN.matcher(version).matches();
    }

    @Override
    public int compareTo(SemVer other) {
        // Compare major.minor.patch numerically
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.patch, other.patch);
        if (cmp != 0) return cmp;

        // Pre-release comparison per semver 2.0.0 spec:
        // - A version WITHOUT prerelease has HIGHER precedence than same version WITH prerelease
        if (this.prerelease == null && other.prerelease == null) return 0;
        if (this.prerelease == null) return 1;  // this is release, other is prerelease
        if (other.prerelease == null) return -1; // this is prerelease, other is release

        // Both have prerelease — compare dot-separated identifiers
        String[] thisParts = this.prerelease.split("\\.");
        String[] otherParts = other.prerelease.split("\\.");

        int length = Math.min(thisParts.length, otherParts.length);
        for (int i = 0; i < length; i++) {
            cmp = compareIdentifiers(thisParts[i], otherParts[i]);
            if (cmp != 0) return cmp;
        }

        // Fewer fields < more fields (if all preceding are equal)
        return Integer.compare(thisParts.length, otherParts.length);
    }

    private static int compareIdentifiers(String a, String b) {
        boolean aNumeric = isNumeric(a);
        boolean bNumeric = isNumeric(b);

        if (aNumeric && bNumeric) {
            // Both numeric: compare as integers
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        }
        if (aNumeric) {
            // Numeric < alphanumeric
            return -1;
        }
        if (bNumeric) {
            return 1;
        }
        // Both alphanumeric: compare lexically
        return a.compareTo(b);
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemVer other)) return false;
        return major == other.major &&
                minor == other.minor &&
                patch == other.patch &&
                Objects.equals(prerelease, other.prerelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, prerelease);
    }

    @Override
    public String toString() {
        if (prerelease == null) {
            return major + "." + minor + "." + patch;
        }
        return major + "." + minor + "." + patch + "-" + prerelease;
    }
}
