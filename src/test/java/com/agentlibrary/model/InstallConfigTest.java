package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstallConfigTest {

    @Test
    void validConstruction() {
        InstallConfig config = new InstallConfig(".claude/", List.of("agent.md"), "append");
        assertEquals(".claude/", config.target());
        assertEquals(List.of("agent.md"), config.files());
        assertEquals("append", config.merge());
    }

    @Test
    void filesDefensivelyCopied() {
        List<String> files = new ArrayList<>();
        files.add("a.md");
        InstallConfig config = new InstallConfig(null, files, null);
        files.add("b.md");
        assertEquals(1, config.files().size(), "Mutating source should not affect record");
    }

    @Test
    void nullFilesBecomesEmptyList() {
        InstallConfig config = new InstallConfig(null, null, null);
        assertNotNull(config.files());
        assertEquals(0, config.files().size());
    }

    @Test
    void filesUnmodifiable() {
        InstallConfig config = new InstallConfig(null, List.of("a.md"), null);
        assertThrows(UnsupportedOperationException.class, () -> config.files().add("b.md"));
    }

    @Test
    void nullTargetAndMergeAllowed() {
        InstallConfig config = new InstallConfig(null, List.of(), null);
        assertNull(config.target());
        assertNull(config.merge());
    }
}
