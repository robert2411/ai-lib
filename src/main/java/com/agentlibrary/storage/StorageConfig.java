package com.agentlibrary.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the storage layer.
 * Enables configuration properties and defines storage beans.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class StorageConfig {

    @Bean
    public WriteQueue writeQueue() {
        return new WriteQueue();
    }

    @Bean
    public ArtifactRepository artifactRepository(AppProperties props, WriteQueue writeQueue) {
        return new LocalGitArtifactRepository(props, writeQueue);
    }
}
