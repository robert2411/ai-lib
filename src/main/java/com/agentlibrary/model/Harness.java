package com.agentlibrary.model;

/**
 * Enumerates supported AI harnesses / coding assistants.
 */
public enum Harness {

    CLAUDE("claude"),
    COPILOT("copilot");

    private final String slug;

    Harness(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    /**
     * Returns the Harness matching the given slug.
     *
     * @param slug the slug to look up (e.g. "claude", "copilot")
     * @return the matching Harness
     * @throws IllegalArgumentException if no match is found
     */
    public static Harness fromSlug(String slug) {
        for (Harness h : values()) {
            if (h.slug.equals(slug)) {
                return h;
            }
        }
        throw new IllegalArgumentException("Unknown Harness slug: " + slug);
    }
}
