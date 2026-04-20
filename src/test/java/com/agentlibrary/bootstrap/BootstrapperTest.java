package com.agentlibrary.bootstrap;

import com.agentlibrary.index.IndexService;
import com.agentlibrary.model.*;
import com.agentlibrary.service.ArtifactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Bootstrapper component.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapperTest {

    @Mock
    private ArtifactService artifactService;

    @Mock
    private IndexService indexService;

    @Mock
    private ApplicationReadyEvent event;

    private Bootstrapper bootstrapper;

    @BeforeEach
    void setUp() {
        bootstrapper = new Bootstrapper(artifactService, indexService);
    }

    @Test
    void onApplicationEvent_emptyRepo_seedsArtifacts() {
        when(indexService.getAll()).thenReturn(List.of());
        when(artifactService.createOrUpdate(any(String.class), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "seeded"));

        bootstrapper.onApplicationEvent(event);

        // Should call createOrUpdate for each seed file (5 total)
        verify(artifactService, times(5)).createOrUpdate(any(String.class), any(User.class));
    }

    @Test
    void onApplicationEvent_nonEmptyRepo_skipsSeeding() {
        ArtifactMetadata existing = new ArtifactMetadata(
                "existing", "Existing", ArtifactType.SKILL, "1.0.0",
                "desc", List.of(Harness.CLAUDE), List.of(),
                null, null, null, null,
                Instant.now(), null, null, null, null
        );
        when(indexService.getAll()).thenReturn(List.of(existing));

        bootstrapper.onApplicationEvent(event);

        // Should NOT call createOrUpdate at all
        verify(artifactService, never()).createOrUpdate(any(String.class), any(User.class));
    }

    @Test
    void seedAll_oneFailureDoesNotPreventOthers() {
        when(indexService.getAll()).thenReturn(List.of());

        // First call succeeds, second throws, rest succeed
        when(artifactService.createOrUpdate(any(String.class), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "seeded"))
                .thenThrow(new RuntimeException("Simulated failure"))
                .thenReturn(new CommitResult("def456", Instant.now(), "seeded"))
                .thenReturn(new CommitResult("ghi789", Instant.now(), "seeded"))
                .thenReturn(new CommitResult("jkl012", Instant.now(), "seeded"));

        bootstrapper.onApplicationEvent(event);

        // All 5 seeds should be attempted despite the failure on the 2nd
        verify(artifactService, times(5)).createOrUpdate(any(String.class), any(User.class));
    }

    @Test
    void seedAll_usesSystemUser() {
        when(indexService.getAll()).thenReturn(List.of());
        when(artifactService.createOrUpdate(any(String.class), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "seeded"));

        bootstrapper.onApplicationEvent(event);

        // Verify the User passed has username "system" and ADMIN role
        verify(artifactService, atLeastOnce()).createOrUpdate(
                any(String.class),
                argThat(user -> "system".equals(user.username()) && user.roles().contains("ADMIN"))
        );
    }

    @Test
    void seedAll_seedsIndividualsBeforeGroup() {
        when(indexService.getAll()).thenReturn(List.of());
        when(artifactService.createOrUpdate(any(String.class), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "seeded"));

        bootstrapper.onApplicationEvent(event);

        // Verify order: capture all content strings passed
        var inOrder = inOrder(artifactService);
        // First 4 should be individual artifacts (not agent-group)
        inOrder.verify(artifactService, times(4)).createOrUpdate(
                argThat(content -> !content.contains("type: agent-group")),
                any(User.class)
        );
        // Last one should be the agent-group
        inOrder.verify(artifactService).createOrUpdate(
                argThat(content -> content.contains("type: agent-group")),
                any(User.class)
        );
    }

    @Test
    void seedAll_seedContentParsesCorrectly() {
        // Verify that the actual seed files can be read from classpath
        when(indexService.getAll()).thenReturn(List.of());
        when(artifactService.createOrUpdate(any(String.class), any(User.class)))
                .thenReturn(new CommitResult("abc123", Instant.now(), "seeded"));

        // This should not throw — all seed files must be readable
        bootstrapper.onApplicationEvent(event);

        // Verify content passed contains frontmatter marker
        verify(artifactService, atLeastOnce()).createOrUpdate(
                argThat(content -> content.startsWith("---")),
                any(User.class)
        );
    }
}
