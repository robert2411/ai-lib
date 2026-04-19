package com.agentlibrary.index;

import com.agentlibrary.storage.LocalGitArtifactRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration that wires IndexService refresh callback to the repository
 * and connects SearchService for index rebuilds.
 */
@Configuration
public class IndexConfig {

    /**
     * Wires the refresh callback from repository to IndexService after all beans are initialised.
     * Also connects SearchService to IndexService for rebuild-on-refresh.
     */
    @Bean
    public CommandLineRunner wireIndexRefreshCallback(
            LocalGitArtifactRepository repository,
            IndexService indexService,
            SearchService searchService) {
        return args -> {
            // Connect SearchService so refresh() triggers rebuild()
            indexService.setSearchService(searchService);

            // Wire repository commit callback to trigger index refresh
            repository.setRefreshCallback(indexService::refresh);

            // Perform initial rebuild of search index with current data
            searchService.rebuild(indexService.getAll());
        };
    }
}
