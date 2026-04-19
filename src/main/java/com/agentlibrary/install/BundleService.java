package com.agentlibrary.install;

import com.agentlibrary.model.ArtifactType;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.ResolvedGroupMember;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service that creates zip bundles for install operations.
 * Wraps ArtifactRepository.bundle() (tar.gz) and re-packages into a combined zip archive.
 */
@Service
public class BundleService {

    private final ArtifactService artifactService;

    public BundleService(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    /**
     * Creates a zip archive containing all artifacts for the given slugs.
     * Each artifact's files are placed under a {slug}/ prefix in the zip.
     * Agent-group slugs are expanded to their member artifacts.
     *
     * @param slugs list of artifact slugs to bundle
     * @return byte array of the zip archive
     * @throws IllegalArgumentException if slugs is null or empty
     */
    public byte[] bundleAsZip(List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            throw new IllegalArgumentException("slugs must not be null or empty");
        }

        // Expand group slugs into individual artifact slugs
        List<String> expandedSlugs = expandSlugs(slugs);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(baos)) {

            for (String slug : expandedSlugs) {
                InputStream tarGzStream = artifactService.bundle(slug, "latest");
                extractTarGzIntoZip(tarGzStream, slug, zipOut);
            }

            zipOut.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to create install bundle zip", e);
        }
    }

    /**
     * Expands agent-group slugs into their member slugs.
     * Non-group slugs are passed through unchanged.
     */
    private List<String> expandSlugs(List<String> slugs) {
        List<String> expanded = new ArrayList<>();
        for (String slug : slugs) {
            var artifact = artifactService.get(slug, "latest");
            if (artifact.metadata().type() == ArtifactType.AGENT_GROUP) {
                List<ResolvedGroupMember> members = artifactService.resolveGroup(slug);
                for (ResolvedGroupMember member : members) {
                    expanded.add(member.slug());
                }
            } else {
                expanded.add(slug);
            }
        }
        return expanded;
    }

    /**
     * Extracts entries from a tar.gz input stream and writes them into the zip
     * under a {slug}/ prefix. Sanitizes entry names to prevent zip-slip/path traversal.
     */
    private void extractTarGzIntoZip(InputStream tarGzStream, String slug, ZipOutputStream zipOut) throws IOException {
        try (GzipCompressorInputStream gzIn = new GzipCompressorInputStream(tarGzStream);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzIn)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String sanitizedName = sanitizeEntryName(entry.getName());
                if (sanitizedName == null) {
                    continue; // skip malicious entries
                }
                String zipEntryName = slug + "/" + sanitizedName;
                zipOut.putNextEntry(new ZipEntry(zipEntryName));

                byte[] buffer = new byte[8192];
                int len;
                while ((len = tarIn.read(buffer)) != -1) {
                    zipOut.write(buffer, 0, len);
                }
                zipOut.closeEntry();
            }
        }
    }

    /**
     * Sanitizes a tar entry name to prevent path traversal attacks (zip-slip).
     * Returns null if the entry name is malicious and should be skipped.
     */
    private static String sanitizeEntryName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        // Normalize path separators and resolve relative segments
        String normalized = name.replace('\\', '/');
        // Reject entries with path traversal
        if (normalized.contains("../") || normalized.startsWith("/") || normalized.startsWith("..")) {
            return null;
        }
        // Strip leading ./ if present
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.isBlank()) {
            return null;
        }
        return normalized;
    }
}
