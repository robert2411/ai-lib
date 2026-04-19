package com.agentlibrary.metadata;

import com.agentlibrary.model.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates ArtifactMetadata against per-type schemas, slug rules, and semver format.
 * Collects all validation errors and throws a single ValidationException.
 */
public class MetadataValidator {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*[a-z0-9]$");
    private static final Pattern SINGLE_CHAR_SLUG = Pattern.compile("^[a-z]$");
    private static final Pattern CONSECUTIVE_HYPHENS = Pattern.compile("--");

    private MetadataValidator() {
    }

    /**
     * Validates the given metadata against all applicable rules.
     *
     * @param metadata the metadata to validate
     * @throws ValidationException if any validation errors are found
     */
    public static void validate(ArtifactMetadata metadata) throws ValidationException {
        List<String> errors = new ArrayList<>();

        validateSlug(metadata.name(), errors);
        validateSemVer(metadata.version(), errors);
        validateRequiredFields(metadata, errors);
        validateHarnesses(metadata, errors);
        validateAgentGroup(metadata, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static void validateSlug(String slug, List<String> errors) {
        if (slug == null || slug.isBlank()) {
            errors.add("name (slug) must not be null or blank");
            return;
        }

        // Single character slug must be lowercase letter
        if (slug.length() == 1) {
            if (!SINGLE_CHAR_SLUG.matcher(slug).matches()) {
                errors.add("name (slug) must be a lowercase letter for single-character slugs: " + slug);
            }
            return;
        }

        if (!SLUG_PATTERN.matcher(slug).matches()) {
            errors.add("name (slug) must match pattern [a-z][a-z0-9-]*[a-z0-9]: " + slug);
        }

        if (CONSECUTIVE_HYPHENS.matcher(slug).find()) {
            errors.add("name (slug) must not contain consecutive hyphens: " + slug);
        }
    }

    private static void validateSemVer(String version, List<String> errors) {
        if (version == null || version.isBlank()) {
            errors.add("version must not be null or blank");
            return;
        }
        if (!SemVer.isValid(version)) {
            errors.add("version is not valid semver: " + version);
        }
    }

    private static void validateRequiredFields(ArtifactMetadata metadata, List<String> errors) {
        TypeSchema schema = TypeSchemaRegistry.forType(metadata.type());

        for (String field : schema.requiredFields()) {
            Object value = getFieldValue(metadata, field);
            if (value == null) {
                errors.add("Required field '" + field + "' is missing for type " + metadata.type().slug());
            } else if (value instanceof String s && s.isBlank()) {
                errors.add("Required field '" + field + "' must not be blank for type " + metadata.type().slug());
            } else if (value instanceof List<?> list && list.isEmpty() && "members".equals(field)) {
                errors.add("Required field 'members' must not be empty for type " + metadata.type().slug());
            }
        }
    }

    private static void validateHarnesses(ArtifactMetadata metadata, List<String> errors) {
        TypeSchema schema = TypeSchemaRegistry.forType(metadata.type());

        if (schema.allowsAllHarnesses() || metadata.harnesses().isEmpty()) {
            return;
        }

        for (Harness h : metadata.harnesses()) {
            if (!schema.allowedHarnesses().contains(h)) {
                errors.add("Harness '" + h.slug() + "' is not allowed for type " + metadata.type().slug()
                        + ". Allowed: " + schema.allowedHarnesses());
            }
        }
    }

    private static void validateAgentGroup(ArtifactMetadata metadata, List<String> errors) {
        if (metadata.type() != ArtifactType.AGENT_GROUP) {
            return;
        }

        if (metadata.members().isEmpty()) {
            errors.add("agent-group type requires at least one member");
        }

        // Check for recognised roles
        boolean hasRecognisedRole = false;
        for (AgentGroupMember member : metadata.members()) {
            if (member.hasRecognisedRole()) {
                hasRecognisedRole = true;
                break;
            }
        }
        if (!metadata.members().isEmpty() && !hasRecognisedRole) {
            errors.add("agent-group must have at least one member with a recognised role");
        }

        // Check each member has non-blank slug and role (already enforced by record,
        // but validate for codec-constructed objects)
        for (AgentGroupMember member : metadata.members()) {
            if (!member.hasRecognisedRole()) {
                errors.add("agent-group member '" + member.slug() + "' has unrecognised role: " + member.role());
            }
        }

        // Check for duplicate slugs
        Set<String> slugs = new HashSet<>();
        for (AgentGroupMember member : metadata.members()) {
            if (!slugs.add(member.slug())) {
                errors.add("agent-group has duplicate member slug: " + member.slug());
            }
        }
    }

    private static Object getFieldValue(ArtifactMetadata metadata, String field) {
        return switch (field) {
            case "name" -> metadata.name();
            case "title" -> metadata.title();
            case "type" -> metadata.type();
            case "version" -> metadata.version();
            case "description" -> metadata.description();
            case "harnesses" -> metadata.harnesses().isEmpty() ? null : metadata.harnesses();
            case "tags" -> metadata.tags().isEmpty() ? null : metadata.tags();
            case "category" -> metadata.category();
            case "language" -> metadata.language();
            case "author" -> metadata.author();
            case "visibility" -> metadata.visibility();
            case "members" -> metadata.members();
            default -> metadata.extra().get(field);
        };
    }
}
