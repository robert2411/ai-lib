package com.agentlibrary.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the library storage layer.
 * Binds to the "library" prefix in application.yml.
 */
@ConfigurationProperties(prefix = "library")
public class AppProperties {

    private String repoPath = "./data/library.git";
    private String workPath = "./data/library-work";
    private String usersFile = "./data/users.yaml";

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

    public String getWorkPath() {
        return workPath;
    }

    public void setWorkPath(String workPath) {
        this.workPath = workPath;
    }

    public String getUsersFile() {
        return usersFile;
    }

    public void setUsersFile(String usersFile) {
        this.usersFile = usersFile;
    }
}
