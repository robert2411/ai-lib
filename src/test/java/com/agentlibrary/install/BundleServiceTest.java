package com.agentlibrary.install;

import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
import com.agentlibrary.service.NotFoundException;
import com.agentlibrary.service.ResolvedGroupMember;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BundleService. ArtifactService is mocked.
 */
@ExtendWith(MockitoExtension.class)
class BundleServiceTest {

    @Mock
    private ArtifactService artifactService;

    private BundleService bundleService;

    @BeforeEach
    void setUp() {
        bundleService = new BundleService(artifactService);
    }

    private ArtifactMetadata metadata(String name, ArtifactType type) {
        return new ArtifactMetadata(
                name, name, type, "1.0.0",
                "desc", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null,
                Instant.now(), null, null, null, null
        );
    }

    private Artifact artifact(ArtifactMetadata meta) {
        return new Artifact(meta, "body");
    }

    /**
     * Creates a tar.gz InputStream containing a single file.
     */
    private InputStream createTarGz(String fileName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(baos);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzOut)) {
            byte[] data = content.getBytes();
            TarArchiveEntry entry = new TarArchiveEntry(fileName);
            entry.setSize(data.length);
            tarOut.putArchiveEntry(entry);
            tarOut.write(data);
            tarOut.closeArchiveEntry();
            tarOut.finish();
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Creates a tar.gz InputStream containing multiple files.
     */
    private InputStream createTarGz(List<String> fileNames, List<String> contents) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(baos);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzOut)) {
            for (int i = 0; i < fileNames.size(); i++) {
                byte[] data = contents.get(i).getBytes();
                TarArchiveEntry entry = new TarArchiveEntry(fileNames.get(i));
                entry.setSize(data.length);
                tarOut.putArchiveEntry(entry);
                tarOut.write(data);
                tarOut.closeArchiveEntry();
            }
            tarOut.finish();
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    @Test
    void bundleAsZip_singleSlug_validZipWithExpectedStructure() throws Exception {
        ArtifactMetadata meta = metadata("git-helper", ArtifactType.SKILL);
        when(artifactService.get("git-helper", "latest")).thenReturn(artifact(meta));
        when(artifactService.bundle("git-helper", "latest"))
                .thenReturn(createTarGz("skill.md", "# Git Helper\nSome skill content"));

        byte[] zipBytes = bundleService.bundleAsZip(List.of("git-helper"));

        // Verify zip content
        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }
        assertEquals(1, entryNames.size());
        assertEquals("git-helper/skill.md", entryNames.get(0));
    }

    @Test
    void bundleAsZip_singleSlug_zipContentMatchesSource() throws Exception {
        String content = "# My Skill\nContent here";
        ArtifactMetadata meta = metadata("my-skill", ArtifactType.SKILL);
        when(artifactService.get("my-skill", "latest")).thenReturn(artifact(meta));
        when(artifactService.bundle("my-skill", "latest"))
                .thenReturn(createTarGz("skill.md", content));

        byte[] zipBytes = bundleService.bundleAsZip(List.of("my-skill"));

        // Read content from zip
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zipIn.getNextEntry();
            assertNotNull(entry);
            assertEquals("my-skill/skill.md", entry.getName());
            String extractedContent = new String(zipIn.readAllBytes());
            assertEquals(content, extractedContent);
        }
    }

    @Test
    void bundleAsZip_multipleSlugs_zipContainsFilesFromAll() throws Exception {
        ArtifactMetadata skillMeta = metadata("git-helper", ArtifactType.SKILL);
        ArtifactMetadata agentMeta = metadata("code-reviewer", ArtifactType.AGENT_CLAUDE);

        when(artifactService.get("git-helper", "latest")).thenReturn(artifact(skillMeta));
        when(artifactService.get("code-reviewer", "latest")).thenReturn(artifact(agentMeta));
        when(artifactService.bundle("git-helper", "latest"))
                .thenReturn(createTarGz("skill.md", "skill content"));
        when(artifactService.bundle("code-reviewer", "latest"))
                .thenReturn(createTarGz("agent.md", "agent content"));

        byte[] zipBytes = bundleService.bundleAsZip(List.of("git-helper", "code-reviewer"));

        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }
        assertEquals(2, entryNames.size());
        assertTrue(entryNames.contains("git-helper/skill.md"));
        assertTrue(entryNames.contains("code-reviewer/agent.md"));
    }

    @Test
    void bundleAsZip_unknownSlug_throwsNotFoundException() {
        when(artifactService.get("unknown", "latest"))
                .thenThrow(new NotFoundException("Unknown artifact: unknown"));

        assertThrows(NotFoundException.class,
                () -> bundleService.bundleAsZip(List.of("unknown")));
    }

    @Test
    void bundleAsZip_emptySlugsList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> bundleService.bundleAsZip(List.of()));
    }

    @Test
    void bundleAsZip_nullSlugsList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> bundleService.bundleAsZip(null));
    }

    @Test
    void bundleAsZip_multipleFilesInTarGz_allExtracted() throws Exception {
        ArtifactMetadata meta = metadata("my-mcp", ArtifactType.MCP);
        when(artifactService.get("my-mcp", "latest")).thenReturn(artifact(meta));
        when(artifactService.bundle("my-mcp", "latest"))
                .thenReturn(createTarGz(
                        List.of("mcp.json", "README.md"),
                        List.of("{\"name\": \"my-mcp\"}", "# README")
                ));

        byte[] zipBytes = bundleService.bundleAsZip(List.of("my-mcp"));

        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }
        assertEquals(2, entryNames.size());
        assertTrue(entryNames.contains("my-mcp/mcp.json"));
        assertTrue(entryNames.contains("my-mcp/README.md"));
    }

    @Test
    void bundleAsZip_maliciousEntryNames_areSkipped() throws Exception {
        // Create a tar.gz with a path traversal entry name
        ArtifactMetadata meta = metadata("evil-artifact", ArtifactType.SKILL);
        when(artifactService.get("evil-artifact", "latest")).thenReturn(artifact(meta));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(baos);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzOut)) {
            // Normal entry
            byte[] goodData = "good content".getBytes();
            TarArchiveEntry goodEntry = new TarArchiveEntry("skill.md");
            goodEntry.setSize(goodData.length);
            tarOut.putArchiveEntry(goodEntry);
            tarOut.write(goodData);
            tarOut.closeArchiveEntry();

            // Malicious entry with path traversal
            byte[] evilData = "evil content".getBytes();
            TarArchiveEntry evilEntry = new TarArchiveEntry("../../../etc/passwd");
            evilEntry.setSize(evilData.length);
            tarOut.putArchiveEntry(evilEntry);
            tarOut.write(evilData);
            tarOut.closeArchiveEntry();

            tarOut.finish();
        }
        when(artifactService.bundle("evil-artifact", "latest"))
                .thenReturn(new ByteArrayInputStream(baos.toByteArray()));

        byte[] zipBytes = bundleService.bundleAsZip(List.of("evil-artifact"));

        // Only the legitimate file should be in the zip
        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }
        assertEquals(1, entryNames.size());
        assertEquals("evil-artifact/skill.md", entryNames.get(0));
    }

    @Test
    void bundleAsZip_agentGroup_expandsMembers() throws Exception {
        // Set up group
        ArtifactMetadata groupMeta = new ArtifactMetadata(
                "my-team", "My Team", ArtifactType.AGENT_GROUP, "1.0.0",
                "A team", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null,
                Instant.now(), null, null,
                List.of(new AgentGroupMember("mgr-agent", "manager")), null
        );
        when(artifactService.get("my-team", "latest")).thenReturn(artifact(groupMeta));

        ArtifactMetadata mgrMeta = metadata("mgr-agent", ArtifactType.AGENT_CLAUDE);
        when(artifactService.resolveGroup("my-team")).thenReturn(List.of(
                new ResolvedGroupMember("mgr-agent", "manager", mgrMeta)
        ));
        when(artifactService.bundle("mgr-agent", "latest"))
                .thenReturn(createTarGz("agent.md", "# Manager Agent"));

        byte[] zipBytes = bundleService.bundleAsZip(List.of("my-team"));

        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
        }
        assertEquals(1, entryNames.size());
        assertEquals("mgr-agent/agent.md", entryNames.get(0));
    }
}
