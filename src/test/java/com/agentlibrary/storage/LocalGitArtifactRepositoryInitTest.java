package com.agentlibrary.storage;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalGitArtifactRepositoryInitTest {

    @Test
    void initCreatesBarerepoWhenAbsent(@TempDir Path tempDir) {
        Path repoPath = tempDir.resolve("test.git");
        LocalGitArtifactRepository repo = createRepo(repoPath);

        repo.init();

        assertTrue(Files.exists(repoPath.resolve("HEAD")));
        assertTrue(Files.exists(repoPath.resolve("objects")));
        assertTrue(Files.exists(repoPath.resolve("refs")));

        // Verify it's a valid bare repo
        assertNotNull(repo.getRepository());
        assertTrue(repo.getRepository().isBare());

        repo.close();
    }

    @Test
    void initIsIdempotent(@TempDir Path tempDir) {
        Path repoPath = tempDir.resolve("test.git");
        LocalGitArtifactRepository repo = createRepo(repoPath);

        // First init
        repo.init();
        assertTrue(Files.exists(repoPath.resolve("HEAD")));

        // Close and recreate to simulate restart
        repo.close();

        // Second init should not throw
        LocalGitArtifactRepository repo2 = createRepo(repoPath);
        assertDoesNotThrow(repo2::init);
        assertTrue(repo2.getRepository().isBare());

        repo2.close();
    }

    @Test
    void initOpensExistingRepo(@TempDir Path tempDir) throws IOException {
        Path repoPath = tempDir.resolve("existing.git");

        // Pre-create a bare repo manually
        Repository preCreated = new FileRepositoryBuilder()
                .setGitDir(repoPath.toFile())
                .setBare()
                .build();
        preCreated.create(true);
        preCreated.close();

        // Now open via our class
        LocalGitArtifactRepository repo = createRepo(repoPath);
        repo.init();

        assertNotNull(repo.getRepository());
        assertTrue(repo.getRepository().isBare());

        repo.close();
    }

    @Test
    void appPropertiesDefaultValues() {
        AppProperties props = new AppProperties();
        assertEquals("./data/library.git", props.getRepoPath());
        assertEquals("./data/library-work", props.getWorkPath());
        assertEquals("./data/users.yaml", props.getUsersFile());
    }

    @Test
    void appPropertiesCustomValues() {
        AppProperties props = new AppProperties();
        props.setRepoPath("/custom/repo.git");
        props.setWorkPath("/custom/work");
        props.setUsersFile("/custom/users.yaml");

        assertEquals("/custom/repo.git", props.getRepoPath());
        assertEquals("/custom/work", props.getWorkPath());
        assertEquals("/custom/users.yaml", props.getUsersFile());
    }

    private LocalGitArtifactRepository createRepo(Path repoPath) {
        AppProperties props = new AppProperties();
        props.setRepoPath(repoPath.toString());
        WriteQueue writeQueue = new WriteQueue();
        return new LocalGitArtifactRepository(props, writeQueue);
    }
}
