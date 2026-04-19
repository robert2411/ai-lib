package com.agentlibrary.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the library storage layer.
 * Binds to the "library" prefix in application.yml.
 *
 * <p>{@code dataDir} serves as the base directory. If individual paths
 * ({@code repoPath}, {@code workPath}, {@code usersFile}) are not explicitly
 * set, they default to locations relative to {@code dataDir}.</p>
 */
@ConfigurationProperties(prefix = "library")
public class AppProperties {

    private String dataDir = "./data";
    private String repoPath;
    private String workPath;
    private String usersFile;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getRepoPath() {
        return repoPath != null ? repoPath : dataDir + "/library.git";
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

    public String getWorkPath() {
        return workPath != null ? workPath : dataDir + "/library-work";
    }

    public void setWorkPath(String workPath) {
        this.workPath = workPath;
    }

    public String getUsersFile() {
        return usersFile != null ? usersFile : dataDir + "/users.yaml";
    }

    public void setUsersFile(String usersFile) {
        this.usersFile = usersFile;
    }
}
