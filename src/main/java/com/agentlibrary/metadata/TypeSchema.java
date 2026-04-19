package com.agentlibrary.metadata;

import com.agentlibrary.model.Harness;

import java.util.Set;

/**
 * Defines the schema constraints for a specific artifact type.
 */
public record TypeSchema(
        Set<String> requiredFields,
        Set<String> optionalFields,
        Set<Harness> allowedHarnesses
) {
    /**
     * Returns true if all harnesses are allowed (no restriction).
     */
    public boolean allowsAllHarnesses() {
        return allowedHarnesses == null || allowedHarnesses.isEmpty();
    }
}
