package com.agentlibrary.service;

import com.agentlibrary.index.IndexService;
import com.agentlibrary.index.SearchService;
import com.agentlibrary.metadata.ValidationException;
import com.agentlibrary.model.*;
import com.agentlibrary.storage.ArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ArtifactService. All dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {

    @Mock
    private ArtifactRepository repository;

    @Mock
    private IndexService indexService;

    @Mock
    private SearchService searchService;

    private ArtifactService service;

    private static final String RAW_SKILL_CONTENT = """
            ---
            name: git-helper
            type: skill
            version: 1.0.0
            description: A git helper skill
            harnesses:
              - claude
            tags:
              - git
              - vcs
            ---
            This is the body content.
            """;

    private static final User TEST_USER = new User("editor", Set.of("EDITOR", "USER"));

    private ArtifactMetadata sampleMetadata() {
        return new ArtifactMetadata(
                "git-helper", "Git Helper", ArtifactType.SKILL, "1.0.0",
                "A git helper skill", List.of(Harness.CLAUDE), List.of("git", "vcs"),
                null, null, null, null,
                Instant.parse("2024-01-01T00:00:00Z"), null, null, null, null
        );
    }

    private Artifact sampleArtifact() {
        return new Artifact(sampleMetadata(), "This is the body content.\n");
    }

    @BeforeEach
    void setUp() {
        service = new ArtifactService(repository, indexService, searchService);
    }

    @Test
    void createOrUpdate_happyPath_validatesAndSaves() {
        when(repository.save(any(Artifact.class), anyString(), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "test commit"));

        CommitResult result = service.createOrUpdate(RAW_SKILL_CONTENT, TEST_USER);

        assertNotNull(result);
        assertEquals("abc123", result.commitId());
        verify(repository).save(any(Artifact.class), contains("skill/git-helper"), eq(TEST_USER));
    }

    @Test
    void createOrUpdate_invalidMetadata_throwsValidationException() {
        String invalidContent = """
                ---
                name: INVALID_SLUG
                type: skill
                version: not-semver
                ---
                Body text.
                """;

        assertThrows(ValidationException.class, () ->
                service.createOrUpdate(invalidContent, TEST_USER));

        verify(repository, never()).save(any(), any(), any());
    }

    @Test
    void createOrUpdate_agentGroupWithNonExistentMember_throwsValidationException() {
        String groupContent = """
                ---
                name: my-group
                type: agent-group
                version: 1.0.0
                description: A test group
                members:
                  - slug: non-existent-agent
                    role: manager
                ---
                Group body.
                """;

        when(indexService.getAll()).thenReturn(List.of(sampleMetadata()));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.createOrUpdate(groupContent, TEST_USER));

        assertTrue(ex.getMessage().contains("non-existent-agent"));
        verify(repository, never()).save(any(), any(), any());
    }

    @Test
    void get_happyPath_returnsArtifact() {
        when(indexService.getAll()).thenReturn(List.of(sampleMetadata()));
        when(repository.get("skill", "git-helper", "1.0.0"))
                .thenReturn(Optional.of(sampleArtifact()));

        Artifact result = service.get("git-helper", "1.0.0");

        assertNotNull(result);
        assertEquals("git-helper", result.metadata().name());
    }

    @Test
    void get_unknownSlug_throwsNotFoundException() {
        when(indexService.getAll()).thenReturn(List.of(sampleMetadata()));

        assertThrows(NotFoundException.class, () ->
                service.get("unknown-slug", "1.0.0"));
    }

    @Test
    void get_knownSlugButNotInRepo_throwsNotFoundException() {
        when(indexService.getAll()).thenReturn(List.of(sampleMetadata()));
        when(repository.get("skill", "git-helper", "9.9.9"))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                service.get("git-helper", "9.9.9"));
    }

    @Test
    void search_delegatesToSearchService() {
        Filter filter = new Filter(ArtifactType.SKILL, null, null, null);
        List<ArtifactMetadata> expected = List.of(sampleMetadata());
        when(searchService.search("git", filter)).thenReturn(expected);

        List<ArtifactMetadata> result = service.search("git", filter);

        assertEquals(expected, result);
        verify(searchService).search("git", filter);
    }

    @Test
    void list_delegatesToIndexServiceWithFilter() {
        ArtifactMetadata skillMeta = sampleMetadata();
        ArtifactMetadata promptMeta = new ArtifactMetadata(
                "my-prompt", "My Prompt", ArtifactType.PROMPT, "1.0.0",
                "A prompt", List.of(), List.of("test"), null, null, null, null,
                null, null, null, null, null
        );
        when(indexService.getAll()).thenReturn(List.of(skillMeta, promptMeta));

        Filter filter = new Filter(ArtifactType.SKILL, null, null, null);
        List<ArtifactMetadata> result = service.list(filter, 0, 20);

        assertEquals(1, result.size());
        assertEquals("git-helper", result.get(0).name());
    }

    @Test
    void list_pagination() {
        ArtifactMetadata meta1 = sampleMetadata();
        ArtifactMetadata meta2 = new ArtifactMetadata(
                "other-skill", "Other Skill", ArtifactType.SKILL, "1.0.0",
                "Another", List.of(), List.of(), null, null, null, null,
                null, null, null, null, null
        );
        when(indexService.getAll()).thenReturn(List.of(meta1, meta2));

        List<ArtifactMetadata> page0 = service.list(null, 0, 1);
        List<ArtifactMetadata> page1 = service.list(null, 1, 1);

        assertEquals(1, page0.size());
        assertEquals(1, page1.size());
        assertEquals("git-helper", page0.get(0).name());
        assertEquals("other-skill", page1.get(0).name());
    }

    @Test
    void resolveGroup_returnsMembers_sortedByRoleOrder() {
        // Create agent-group metadata with members in non-sorted order
        ArtifactMetadata groupMeta = new ArtifactMetadata(
                "my-group", "My Group", ArtifactType.AGENT_GROUP, "1.0.0",
                "A group", List.of(), List.of(),
                null, null, null, null, null, null, null,
                List.of(
                        new AgentGroupMember("impl-agent", "implementation"),
                        new AgentGroupMember("mgr-agent", "manager"),
                        new AgentGroupMember("analyser-agent", "analyse")
                ), null
        );
        Artifact groupArtifact = new Artifact(groupMeta, "");

        ArtifactMetadata implMeta = new ArtifactMetadata(
                "impl-agent", "Impl", ArtifactType.AGENT_CLAUDE, "1.0.0",
                "Impl agent", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null, null, null, null, null, null
        );
        ArtifactMetadata mgrMeta = new ArtifactMetadata(
                "mgr-agent", "Mgr", ArtifactType.AGENT_CLAUDE, "1.0.0",
                "Manager agent", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null, null, null, null, null, null
        );
        ArtifactMetadata analyseMeta = new ArtifactMetadata(
                "analyser-agent", "Analyser", ArtifactType.AGENT_CLAUDE, "1.0.0",
                "Analyse agent", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null, null, null, null, null, null
        );

        when(indexService.getAll()).thenReturn(List.of(groupMeta, implMeta, mgrMeta, analyseMeta));
        when(repository.get("agent-group", "my-group", "latest"))
                .thenReturn(Optional.of(groupArtifact));
        when(repository.get("agent-claude", "impl-agent", "latest"))
                .thenReturn(Optional.of(new Artifact(implMeta, "")));
        when(repository.get("agent-claude", "mgr-agent", "latest"))
                .thenReturn(Optional.of(new Artifact(mgrMeta, "")));
        when(repository.get("agent-claude", "analyser-agent", "latest"))
                .thenReturn(Optional.of(new Artifact(analyseMeta, "")));

        List<ResolvedGroupMember> result = service.resolveGroup("my-group");

        assertEquals(3, result.size());
        assertEquals("manager", result.get(0).role());
        assertEquals("analyse", result.get(1).role());
        assertEquals("implementation", result.get(2).role());
    }

    @Test
    void resolveGroup_nonGroupArtifact_throwsIllegalArgumentException() {
        when(indexService.getAll()).thenReturn(List.of(sampleMetadata()));
        when(repository.get("skill", "git-helper", "latest"))
                .thenReturn(Optional.of(sampleArtifact()));

        assertThrows(IllegalArgumentException.class, () ->
                service.resolveGroup("git-helper"));
    }

    @Test
    void delete_delegatesToRepository() {
        when(indexService.getAll()).thenReturn(List.of(sampleMetadata()));

        service.delete("git-helper", TEST_USER);

        verify(repository).delete(eq("skill"), eq("git-helper"), anyString(), eq(TEST_USER));
    }

    @Test
    void getVersions_delegatesToRepository() {
        when(indexService.getAll()).thenReturn(List.of(sampleMetadata()));
        List<VersionRef> versions = List.of(
                new VersionRef("1.0.0", "abc123", Instant.now()));
        when(repository.versions("skill", "git-helper")).thenReturn(versions);

        List<VersionRef> result = service.getVersions("git-helper");

        assertEquals(versions, result);
    }

    @Test
    void bundle_delegatesToRepository() {
        when(indexService.getAll()).thenReturn(List.of(sampleMetadata()));
        InputStream expected = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(repository.bundle("skill", "git-helper", "1.0.0")).thenReturn(expected);

        InputStream result = service.bundle("git-helper", "1.0.0");

        assertSame(expected, result);
    }
}
