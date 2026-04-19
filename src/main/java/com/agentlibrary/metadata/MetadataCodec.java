package com.agentlibrary.metadata;

import com.agentlibrary.model.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Codec for parsing and serialising artifact metadata from/to YAML frontmatter.
 * Frontmatter is delimited by "---" lines at the start of content.
 */
public class MetadataCodec {

    private static final Logger LOG = Logger.getLogger(MetadataCodec.class.getName());
    private static final String DELIMITER = "---";

    private MetadataCodec() {
    }

    /**
     * Decodes raw content (with YAML frontmatter) into an Artifact.
     *
     * @param rawContent the full content with frontmatter
     * @return the decoded Artifact containing metadata and body
     * @throws IllegalArgumentException if content has no frontmatter
     */
    public static Artifact decode(String rawContent) {
        if (!hasFrontmatter(rawContent)) {
            throw new IllegalArgumentException("Content does not contain YAML frontmatter (must start with ---)");
        }

        String[] parts = splitFrontmatter(rawContent);
        String yamlSection = parts[0];
        String body = parts[1];

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> map = yaml.load(yamlSection);
        if (map == null) {
            map = new LinkedHashMap<>();
        }

        ArtifactMetadata metadata = mapToMetadata(map);
        return new Artifact(metadata, body);
    }

    /**
     * Encodes an Artifact back to raw content with YAML frontmatter.
     *
     * @param artifact the artifact to encode
     * @return the encoded string with frontmatter + body
     */
    public static String encode(Artifact artifact) {
        Map<String, Object> map = toMap(artifact.metadata());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        String yamlStr = yaml.dump(map);

        StringBuilder sb = new StringBuilder();
        sb.append(DELIMITER).append("\n");
        sb.append(yamlStr);
        sb.append(DELIMITER).append("\n");
        sb.append(artifact.content());
        return sb.toString();
    }

    /**
     * Extracts the body content after the closing frontmatter delimiter.
     */
    public static String extractBody(String rawContent) {
        if (!hasFrontmatter(rawContent)) {
            return rawContent;
        }
        return splitFrontmatter(rawContent)[1];
    }

    /**
     * Returns true if the content starts with a frontmatter delimiter.
     */
    public static boolean hasFrontmatter(String rawContent) {
        return rawContent != null && rawContent.startsWith(DELIMITER);
    }

    // --- Internal helpers ---

    private static String[] splitFrontmatter(String rawContent) {
        // First line is "---", find the second "---"
        int firstDelimEnd = rawContent.indexOf('\n');
        if (firstDelimEnd == -1) {
            return new String[]{"", ""};
        }

        int secondDelimStart = rawContent.indexOf("\n" + DELIMITER + "\n", firstDelimEnd);
        if (secondDelimStart == -1) {
            // Try with end-of-string (no trailing newline after second ---)
            secondDelimStart = rawContent.indexOf("\n" + DELIMITER, firstDelimEnd);
            if (secondDelimStart == -1) {
                throw new IllegalArgumentException("Frontmatter is not properly closed with ---");
            }
        }

        String yamlSection = rawContent.substring(firstDelimEnd + 1, secondDelimStart);
        int bodyStart = rawContent.indexOf('\n', secondDelimStart + 1);
        String body = bodyStart == -1 ? "" : rawContent.substring(bodyStart + 1);

        return new String[]{yamlSection, body};
    }

    /**
     * Converts a map of string keys to an ArtifactMetadata record.
     * Used by IndexFile and other components that need to deserialise metadata from maps.
     *
     * @param map the key-value map representing metadata
     * @return the parsed ArtifactMetadata
     */
    @SuppressWarnings("unchecked")
    public static ArtifactMetadata metadataFromMap(Map<String, Object> map) {
        return mapToMetadata(map);
    }

    /**
     * Converts an ArtifactMetadata record to a map of string keys.
     * Used by IndexFile and other components that need to serialise metadata to maps.
     *
     * @param meta the metadata to convert
     * @return the map representation
     */
    public static Map<String, Object> metadataToMap(ArtifactMetadata meta) {
        return toMap(meta);
    }

    @SuppressWarnings("unchecked")
    private static ArtifactMetadata mapToMetadata(Map<String, Object> map) {
        String name = getString(map, "name");
        String title = getString(map, "title");
        ArtifactType type = map.containsKey("type") ? ArtifactType.fromSlug(getString(map, "type")) : null;
        String version = getString(map, "version");
        String description = getString(map, "description");

        List<Harness> harnesses = null;
        if (map.containsKey("harnesses")) {
            Object hObj = map.get("harnesses");
            if (hObj instanceof List<?> hList) {
                harnesses = hList.stream()
                        .map(Object::toString)
                        .map(Harness::fromSlug)
                        .collect(Collectors.toList());
            }
        }

        List<String> tags = null;
        if (map.containsKey("tags")) {
            Object tObj = map.get("tags");
            if (tObj instanceof List<?> tList) {
                tags = tList.stream().map(Object::toString).collect(Collectors.toList());
            }
        }

        String category = getString(map, "category");
        String language = getString(map, "language");
        String author = getString(map, "author");

        Visibility visibility = null;
        if (map.containsKey("visibility")) {
            visibility = Visibility.fromSlug(getString(map, "visibility"));
        }

        Instant created = parseInstant(map, "created");
        Instant updated = parseInstant(map, "updated");

        InstallConfig install = null;
        if (map.containsKey("install")) {
            Object iObj = map.get("install");
            if (iObj instanceof Map<?, ?> iMap) {
                String target = iMap.containsKey("target") ? iMap.get("target").toString() : null;
                List<String> files = null;
                if (iMap.containsKey("files") && iMap.get("files") instanceof List<?> fList) {
                    files = fList.stream().map(Object::toString).collect(Collectors.toList());
                }
                String merge = iMap.containsKey("merge") ? iMap.get("merge").toString() : null;
                install = new InstallConfig(target, files, merge);
            }
        }

        List<AgentGroupMember> members = null;
        if (map.containsKey("members")) {
            Object mObj = map.get("members");
            if (mObj instanceof List<?> mList) {
                members = mList.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> (Map<String, Object>) m)
                        .map(m -> new AgentGroupMember(
                                m.get("slug") != null ? m.get("slug").toString() : "",
                                m.get("role") != null ? m.get("role").toString() : ""
                        ))
                        .collect(Collectors.toList());
            }
        }

        // Extra fields: anything not in the known set
        Map<String, Object> extra = new LinkedHashMap<>();
        Set<String> knownKeys = Set.of("name", "title", "type", "version", "description",
                "harnesses", "tags", "category", "language", "author", "visibility",
                "created", "updated", "install", "members");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!knownKeys.contains(entry.getKey())) {
                extra.put(entry.getKey(), entry.getValue());
            }
        }

        return new ArtifactMetadata(name, title, type, version, description,
                harnesses, tags, category, language, author, visibility,
                created, updated, install, members, extra.isEmpty() ? null : extra);
    }

    private static Map<String, Object> toMap(ArtifactMetadata meta) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("name", meta.name());
        putIfNotNull(map, "title", meta.title());
        map.put("type", meta.type().slug());
        map.put("version", meta.version());
        putIfNotNull(map, "description", meta.description());

        if (!meta.harnesses().isEmpty()) {
            map.put("harnesses", meta.harnesses().stream()
                    .map(Harness::slug)
                    .collect(Collectors.toList()));
        }

        if (!meta.tags().isEmpty()) {
            map.put("tags", new ArrayList<>(meta.tags()));
        }

        putIfNotNull(map, "category", meta.category());
        putIfNotNull(map, "language", meta.language());
        putIfNotNull(map, "author", meta.author());

        if (meta.visibility() != null) {
            map.put("visibility", meta.visibility().slug());
        }

        if (meta.created() != null) {
            map.put("created", meta.created().toString());
        }
        if (meta.updated() != null) {
            map.put("updated", meta.updated().toString());
        }

        if (meta.install() != null) {
            Map<String, Object> installMap = new LinkedHashMap<>();
            putIfNotNull(installMap, "target", meta.install().target());
            if (!meta.install().files().isEmpty()) {
                installMap.put("files", new ArrayList<>(meta.install().files()));
            }
            putIfNotNull(installMap, "merge", meta.install().merge());
            if (!installMap.isEmpty()) {
                map.put("install", installMap);
            }
        }

        if (!meta.members().isEmpty()) {
            List<Map<String, Object>> membersList = meta.members().stream()
                    .map(m -> {
                        Map<String, Object> mMap = new LinkedHashMap<>();
                        mMap.put("slug", m.slug());
                        mMap.put("role", m.role());
                        return mMap;
                    })
                    .collect(Collectors.toList());
            map.put("members", membersList);
        }

        if (!meta.extra().isEmpty()) {
            map.putAll(meta.extra());
        }

        return map;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private static Instant parseInstant(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof java.util.Date date) {
            return date.toInstant();
        }
        String str = val.toString();
        try {
            return Instant.parse(str);
        } catch (DateTimeParseException e) {
            LOG.warning("Malformed timestamp for key '" + key + "': " + str);
            throw new IllegalArgumentException("Invalid timestamp for field '" + key + "': " + str, e);
        }
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
