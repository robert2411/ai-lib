package com.agentlibrary.model;

/**
 * Enumerates artifact visibility levels.
 */
public enum Visibility {

    TEAM("team"),
    PRIVATE("private");

    private final String slug;

    Visibility(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    /**
     * Returns the Visibility matching the given slug.
     *
     * @param slug the slug to look up (e.g. "team", "private")
     * @return the matching Visibility
     * @throws IllegalArgumentException if no match is found
     */
    public static Visibility fromSlug(String slug) {
        for (Visibility v : values()) {
            if (v.slug.equals(slug)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unknown Visibility slug: " + slug);
    }
}
