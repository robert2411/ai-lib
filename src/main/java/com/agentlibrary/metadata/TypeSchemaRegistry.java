package com.agentlibrary.metadata;

import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.model.Harness;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry that loads per-type schema definitions from classpath YAML files.
 * Schema files are located at schemas/{type-slug}.yaml.
 */
public class TypeSchemaRegistry {

    private static final Map<ArtifactType, TypeSchema> CACHE = new ConcurrentHashMap<>();
    private static final TypeSchema DEFAULT_SCHEMA = new TypeSchema(
            Set.of("name", "type", "version"),
            Set.of(),
            null // all harnesses allowed
    );

    private TypeSchemaRegistry() {
    }

    /**
     * Returns the TypeSchema for the given artifact type.
     * Loads from classpath YAML file, falling back to a default schema.
     */
    public static TypeSchema forType(ArtifactType type) {
        return CACHE.computeIfAbsent(type, TypeSchemaRegistry::loadSchema);
    }

    @SuppressWarnings("unchecked")
    private static TypeSchema loadSchema(ArtifactType type) {
        String path = "schemas/" + type.slug() + ".yaml";
        InputStream is = TypeSchemaRegistry.class.getClassLoader().getResourceAsStream(path);
        if (is == null) {
            return DEFAULT_SCHEMA;
        }

        try (is) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(is);
            if (map == null) {
                return DEFAULT_SCHEMA;
            }

            Set<String> required = new LinkedHashSet<>();
            if (map.containsKey("required") && map.get("required") instanceof List<?> rList) {
                required = rList.stream().map(Object::toString).collect(Collectors.toCollection(LinkedHashSet::new));
            }

            Set<String> optional = new LinkedHashSet<>();
            if (map.containsKey("optional") && map.get("optional") instanceof List<?> oList) {
                optional = oList.stream().map(Object::toString).collect(Collectors.toCollection(LinkedHashSet::new));
            }

            Set<Harness> allowedHarnesses = null;
            if (map.containsKey("allowedHarnesses")) {
                Object ahObj = map.get("allowedHarnesses");
                if (ahObj instanceof String s && "all".equals(s)) {
                    allowedHarnesses = null; // all allowed
                } else if (ahObj instanceof List<?> ahList) {
                    allowedHarnesses = ahList.stream()
                            .map(Object::toString)
                            .map(Harness::fromSlug)
                            .collect(Collectors.toSet());
                }
            }

            return new TypeSchema(
                    Collections.unmodifiableSet(required),
                    Collections.unmodifiableSet(optional),
                    allowedHarnesses != null ? Collections.unmodifiableSet(allowedHarnesses) : null
            );
        } catch (java.io.IOException e) {
            return DEFAULT_SCHEMA;
        }
    }
}
