package com.agentlibrary.storage;

import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.model.ArtifactMetadata;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for reading and writing the INDEX.yaml file that maintains
 * a list of artifact metadata entries in the git repository.
 * <p>
 * This class is decoupled from git — it takes/returns raw YAML strings.
 * The caller (LocalGitArtifactRepository) handles reading/writing to git.
 */
public class IndexFile {

    private IndexFile() {
    }

    /**
     * Parses INDEX.yaml content into a list of ArtifactMetadata entries.
     * Returns an empty list if content is null, blank, or missing the 'artifacts' key.
     *
     * @param yamlContent raw YAML string (may be null or empty)
     * @return list of artifact metadata entries (never null)
     */
    @SuppressWarnings("unchecked")
    public static List<ArtifactMetadata> load(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return List.of();
        }

        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object loaded = yaml.load(yamlContent);
        if (!(loaded instanceof Map<?, ?> root)) {
            return List.of();
        }

        Object artifactsObj = root.get("artifacts");
        if (!(artifactsObj instanceof List<?> artifactsList)) {
            return List.of();
        }

        return artifactsList.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(MetadataCodec::metadataFromMap)
                .collect(Collectors.toList());
    }

    /**
     * Serialises a list of ArtifactMetadata entries to INDEX.yaml format.
     *
     * @param entries the metadata entries to serialise
     * @return YAML string in INDEX.yaml format
     */
    public static String save(List<ArtifactMetadata> entries) {
        List<Map<String, Object>> artifactMaps = entries.stream()
                .map(MetadataCodec::metadataToMap)
                .collect(Collectors.toList());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("artifacts", artifactMaps);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setExplicitStart(true);

        Yaml yaml = new Yaml(options);
        return yaml.dump(root);
    }
}
