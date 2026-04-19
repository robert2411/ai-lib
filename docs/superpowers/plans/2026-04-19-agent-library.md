# Agent Library Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a team-hosted library server (Spring Boot + Thymeleaf + HTMX, backed by a local bare git repo) that stores typed agent artifacts (Claude skills, Claude/Copilot agents, MCP configs, commands, hooks, CLAUDE.md snippets, OpenCode configs, prompts), plus a companion CLI + Claude skill that installs artifacts into the correct paths per harness.

**Architecture:** Single Spring Boot 3 app. JGit for storage (`LocalGitArtifactRepository` implementing a pluggable interface). In-memory Lucene `RAMDirectory` for search. Thymeleaf fragments + HTMX for UI — no frontend build step. File-backed Spring Security users. Install skill lives in the library itself (self-hosted). Deployed as a single container with a persistent `/data` volume.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Security, Thymeleaf, HTMX 2.x, JGit 6.x, Apache Lucene 9.x (RAMDirectory), bucket4j, SnakeYAML, Jackson, Maven, Eclipse Temurin 21, Docker, bats-core, Testcontainers, Monaco editor (static asset).

---

## Conventions Used In This Plan

- **Commits are TDD-sized:** test first, see it fail, implement, see it pass, commit.
- Java package root: `com.agentlibrary`.
- Module layout is single Maven module (no multi-module split).
- "Run tests" always means `./mvnw test` scoped to the new class unless stated.
- When a task adds a class plus its test, the commit message uses Conventional Commits (`feat:`, `test:`, `refactor:`, `chore:`, `docs:`).
- All new Java files start with the package declaration, no `// Copyright` headers.
- Assume the engineer starts in a clean `main` branch with only `agent-loop.sh`, `ai-library.iml`, `backlog/`, `docs/`.

---

## File Structure Overview

```
ai-library/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/                         # Maven wrapper
├── Dockerfile
├── docker-compose.yml
├── scripts/
│   └── gen-password-hash.sh
├── src/main/java/com/agentlibrary/
│   ├── AgentLibraryApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── RateLimitConfig.java
│   │   └── AppProperties.java
│   ├── domain/
│   │   ├── ArtifactType.java
│   │   ├── Harness.java
│   │   ├── Visibility.java
│   │   ├── ArtifactMetadata.java
│   │   ├── Artifact.java
│   │   ├── Filter.java
│   │   ├── VersionRef.java
│   │   ├── CommitResult.java
│   │   └── User.java
│   ├── metadata/
│   │   ├── MetadataCodec.java                    # frontmatter <-> YAML
│   │   ├── MetadataValidator.java
│   │   ├── SemVer.java
│   │   └── TypeSchemaRegistry.java
│   ├── storage/
│   │   ├── ArtifactRepository.java               # interface
│   │   ├── LocalGitArtifactRepository.java
│   │   ├── WriteQueue.java
│   │   ├── IndexFile.java                        # INDEX.yaml read/write
│   │   └── Bootstrapper.java                     # first-run seed
│   ├── index/
│   │   ├── IndexService.java
│   │   ├── SearchService.java                    # Lucene-backed
│   │   └── FacetService.java
│   ├── install/
│   │   ├── InstallManifest.java
│   │   ├── InstallManifestResolver.java
│   │   └── BundleService.java
│   ├── security/
│   │   ├── FileUserDetailsService.java
│   │   ├── UsersFile.java
│   │   ├── LoginRateLimiter.java
│   │   └── BcryptCli.java                        # used by gen-password-hash.sh
│   ├── web/
│   │   ├── api/
│   │   │   ├── ArtifactApiController.java
│   │   │   ├── InstallApiController.java
│   │   │   ├── AdminApiController.java
│   │   │   ├── LookupApiController.java          # /tags /harnesses /types
│   │   │   └── ApiExceptionHandler.java
│   │   └── ui/
│   │       ├── DashboardController.java
│   │       ├── BrowseController.java
│   │       ├── ArtifactViewController.java
│   │       ├── EditController.java
│   │       ├── AdminController.java
│   │       └── WebExceptionHandler.java
│   └── service/
│       └── ArtifactService.java                  # orchestrates metadata+repo+index
├── src/main/resources/
│   ├── application.yml
│   ├── application-prod.yml
│   ├── templates/
│   │   ├── layout.html
│   │   ├── fragments/{header.html, artifact-card.html, filters.html, pagination.html}
│   │   ├── dashboard.html
│   │   ├── browse.html
│   │   ├── artifact.html
│   │   ├── new.html
│   │   ├── edit.html
│   │   ├── admin.html
│   │   └── login.html
│   ├── static/
│   │   ├── css/app.css
│   │   ├── js/htmx.min.js
│   │   └── vendor/monaco/                        # copied at build
│   └── schema/
│       ├── common.yaml
│       ├── skill.yaml
│       ├── agent-claude.yaml
│       ├── agent-copilot.yaml
│       ├── mcp.yaml
│       ├── command.yaml
│       ├── hook.yaml
│       ├── claude-md.yaml
│       ├── opencode.yaml
│       └── prompt.yaml
├── src/test/java/com/agentlibrary/...            # mirror of main
├── src/test/resources/
│   └── fixtures/                                 # sample artifacts
├── skills-seed/agent-library-install/            # seed content for first-run bootstrap
│   ├── skill.md
│   ├── scripts/agent-lib.sh
│   ├── scripts/agent-lib-common.sh
│   └── README.md
└── test-cli/                                     # bats-core tests for the CLI
    └── *.bats
```

---

## Milestone 1 — Project Scaffold

### Task 1: Initialise Maven project + Spring Boot skeleton

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/agentlibrary/AgentLibraryApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/java/com/agentlibrary/AgentLibraryApplicationTests.java`
- Create: `.gitignore`

- [ ] **Step 1: Write failing context-load test**

```java
package com.agentlibrary;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentLibraryApplicationTests {
    @Test
    void contextLoads() {}
}
```

- [ ] **Step 2: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>
    <groupId>com.agentlibrary</groupId>
    <artifactId>agent-library</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <properties>
        <java.version>21</java.version>
        <jgit.version>6.10.0.202406032230-r</jgit.version>
        <lucene.version>9.11.1</lucene.version>
        <snakeyaml.version>2.2</snakeyaml.version>
        <bucket4j.version>8.10.1</bucket4j.version>
    </properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><optional>true</optional></dependency>
        <dependency><groupId>org.eclipse.jgit</groupId><artifactId>org.eclipse.jgit</artifactId><version>${jgit.version}</version></dependency>
        <dependency><groupId>org.apache.lucene</groupId><artifactId>lucene-core</artifactId><version>${lucene.version}</version></dependency>
        <dependency><groupId>org.apache.lucene</groupId><artifactId>lucene-analysis-common</artifactId><version>${lucene.version}</version></dependency>
        <dependency><groupId>org.apache.lucene</groupId><artifactId>lucene-queryparser</artifactId><version>${lucene.version}</version></dependency>
        <dependency><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId><version>${snakeyaml.version}</version></dependency>
        <dependency><groupId>com.bucket4j</groupId><artifactId>bucket4j-core</artifactId><version>${bucket4j.version}</version></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers</artifactId><version>1.20.1</version><scope>test</scope></dependency>
        <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><version>1.20.1</version><scope>test</scope></dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Write `AgentLibraryApplication.java`**

```java
package com.agentlibrary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentLibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentLibraryApplication.class, args);
    }
}
```

- [ ] **Step 4: Write minimal `application.yml`**

```yaml
spring:
  application:
    name: agent-library
server:
  port: 8080
app:
  repoPath: ./dev-data/library.git
  workPath: ./dev-data/library-work
  usersFile: ./dev-data/users.yaml
logging:
  level:
    root: INFO
    com.agentlibrary: DEBUG
```

- [ ] **Step 5: `.gitignore`**

```
target/
dev-data/
.idea/
*.log
.DS_Store
```

- [ ] **Step 6: Install Maven wrapper**

Run: `mvn -N wrapper:wrapper -Dmaven=3.9.9`
Expected: creates `mvnw`, `mvnw.cmd`, `.mvn/`.

- [ ] **Step 7: Run test**

Run: `./mvnw test -Dtest=AgentLibraryApplicationTests`
Expected: PASS (context loads, default Spring Security disables auth for test by default? No — starter enables it. Test still passes on context load.).

- [ ] **Step 8: Commit**

```bash
git add pom.xml mvnw mvnw.cmd .mvn .gitignore src
git commit -m "chore: bootstrap Spring Boot project skeleton"
```

---

## Milestone 2 — Domain Model & Metadata

### Task 2: Enums + core value objects

**Files:**
- Create: `src/main/java/com/agentlibrary/domain/ArtifactType.java`
- Create: `src/main/java/com/agentlibrary/domain/Harness.java`
- Create: `src/main/java/com/agentlibrary/domain/Visibility.java`
- Create: `src/test/java/com/agentlibrary/domain/ArtifactTypeTest.java`

- [ ] **Step 1: Write failing test for `ArtifactType.fromSlug`**

```java
package com.agentlibrary.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ArtifactTypeTest {
    @Test
    void fromSlug_mapsEachValue() {
        assertThat(ArtifactType.fromSlug("skill")).isEqualTo(ArtifactType.SKILL);
        assertThat(ArtifactType.fromSlug("agent-claude")).isEqualTo(ArtifactType.AGENT_CLAUDE);
        assertThat(ArtifactType.fromSlug("agent-copilot")).isEqualTo(ArtifactType.AGENT_COPILOT);
        assertThat(ArtifactType.fromSlug("mcp")).isEqualTo(ArtifactType.MCP);
        assertThat(ArtifactType.fromSlug("command")).isEqualTo(ArtifactType.COMMAND);
        assertThat(ArtifactType.fromSlug("hook")).isEqualTo(ArtifactType.HOOK);
        assertThat(ArtifactType.fromSlug("claude-md")).isEqualTo(ArtifactType.CLAUDE_MD);
        assertThat(ArtifactType.fromSlug("opencode")).isEqualTo(ArtifactType.OPENCODE);
        assertThat(ArtifactType.fromSlug("prompt")).isEqualTo(ArtifactType.PROMPT);
    }

    @Test
    void fromSlug_unknown_throws() {
        assertThatThrownBy(() -> ArtifactType.fromSlug("nope"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void folder_returnsFolderName() {
        assertThat(ArtifactType.SKILL.folder()).isEqualTo("skills");
        assertThat(ArtifactType.AGENT_CLAUDE.folder()).isEqualTo("agents-claude");
        assertThat(ArtifactType.MCP.folder()).isEqualTo("mcp");
    }

    @Test
    void primaryFile_returnsExpectedFilename() {
        assertThat(ArtifactType.SKILL.primaryFile()).isEqualTo("skill.md");
        assertThat(ArtifactType.MCP.primaryFile()).isEqualTo("mcp.json");
        assertThat(ArtifactType.HOOK.primaryFile()).isEqualTo("hook.json");
    }

    @Test
    void hasSidecar_trueForJsonAndYaml() {
        assertThat(ArtifactType.MCP.hasSidecar()).isTrue();
        assertThat(ArtifactType.HOOK.hasSidecar()).isTrue();
        assertThat(ArtifactType.OPENCODE.hasSidecar()).isTrue();
        assertThat(ArtifactType.SKILL.hasSidecar()).isFalse();
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

Run: `./mvnw test -Dtest=ArtifactTypeTest`
Expected: compile error (class missing).

- [ ] **Step 3: Implement `ArtifactType.java`**

```java
package com.agentlibrary.domain;

import java.util.Arrays;

public enum ArtifactType {
    SKILL("skill", "skills", "skill.md", false),
    AGENT_CLAUDE("agent-claude", "agents-claude", "agent.md", false),
    AGENT_COPILOT("agent-copilot", "agents-copilot", "agent.md", false),
    MCP("mcp", "mcp", "mcp.json", true),
    COMMAND("command", "commands", "command.md", false),
    HOOK("hook", "hooks", "hook.json", true),
    CLAUDE_MD("claude-md", "claude-md", "snippet.md", false),
    OPENCODE("opencode", "opencode", "config.yaml", true),
    PROMPT("prompt", "prompts", "prompt.md", false);

    private final String slug;
    private final String folder;
    private final String primaryFile;
    private final boolean hasSidecar;

    ArtifactType(String slug, String folder, String primaryFile, boolean hasSidecar) {
        this.slug = slug;
        this.folder = folder;
        this.primaryFile = primaryFile;
        this.hasSidecar = hasSidecar;
    }

    public String slug() { return slug; }
    public String folder() { return folder; }
    public String primaryFile() { return primaryFile; }
    public boolean hasSidecar() { return hasSidecar; }

    public static ArtifactType fromSlug(String slug) {
        return Arrays.stream(values())
            .filter(t -> t.slug.equals(slug))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown artifact type: " + slug));
    }
}
```

- [ ] **Step 4: Implement `Harness.java`**

```java
package com.agentlibrary.domain;

import java.util.Arrays;

public enum Harness {
    CLAUDE("claude"),
    COPILOT("copilot"),
    OPENCODE("opencode"),
    CODEX("codex"),
    GEMINI("gemini");

    private final String slug;

    Harness(String slug) { this.slug = slug; }

    public String slug() { return slug; }

    public static Harness fromSlug(String slug) {
        return Arrays.stream(values())
            .filter(h -> h.slug.equals(slug))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown harness: " + slug));
    }
}
```

- [ ] **Step 5: Implement `Visibility.java`**

```java
package com.agentlibrary.domain;

public enum Visibility { TEAM, PRIVATE }
```

- [ ] **Step 6: Run test — expect PASS**

Run: `./mvnw test -Dtest=ArtifactTypeTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/agentlibrary/domain src/test/java/com/agentlibrary/domain
git commit -m "feat(domain): add ArtifactType, Harness, Visibility enums"
```

### Task 3: `ArtifactMetadata` record + `Artifact` record

**Files:**
- Create: `src/main/java/com/agentlibrary/domain/ArtifactMetadata.java`
- Create: `src/main/java/com/agentlibrary/domain/Artifact.java`
- Create: `src/main/java/com/agentlibrary/domain/VersionRef.java`
- Create: `src/main/java/com/agentlibrary/domain/CommitResult.java`
- Create: `src/main/java/com/agentlibrary/domain/Filter.java`
- Create: `src/main/java/com/agentlibrary/domain/User.java`

- [ ] **Step 1: Write `ArtifactMetadata.java`**

```java
package com.agentlibrary.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ArtifactMetadata(
    String name,
    String title,
    ArtifactType type,
    String version,                 // semver
    String description,
    List<Harness> harnesses,
    List<String> tags,
    String category,
    String language,
    String author,
    Visibility visibility,
    Instant created,
    Instant updated,
    InstallHints install,
    Map<String, Object> typeExtensions
) {
    public record InstallHints(String target, List<String> files, String merge) {}
}
```

- [ ] **Step 2: Write `Artifact.java`**

```java
package com.agentlibrary.domain;

import java.util.Map;

public record Artifact(
    ArtifactMetadata metadata,
    String primaryContent,            // text content of primary file (UTF-8)
    Map<String, byte[]> extraFiles    // relative path -> bytes for files/**, etc.
) {}
```

- [ ] **Step 3: Write `VersionRef.java`**

```java
package com.agentlibrary.domain;

import java.time.Instant;

public record VersionRef(String version, String commitSha, Instant timestamp) {}
```

- [ ] **Step 4: Write `CommitResult.java`**

```java
package com.agentlibrary.domain;

public record CommitResult(String commitSha, String tagName /* nullable */, boolean created) {}
```

- [ ] **Step 5: Write `Filter.java`**

```java
package com.agentlibrary.domain;

import java.util.List;

public record Filter(
    ArtifactType type,              // nullable
    Harness harness,                // nullable
    List<String> tags,              // may be empty
    String category,                // nullable
    String language,                // nullable
    String author,                  // nullable
    String query                    // free text; nullable
) {
    public static Filter empty() { return new Filter(null, null, List.of(), null, null, null, null); }
}
```

- [ ] **Step 6: Write `User.java`**

```java
package com.agentlibrary.domain;

import java.util.Set;

public record User(String username, String passwordHash, Set<String> roles) {}
```

- [ ] **Step 7: Commit (no behaviour, no tests required yet — pure records)**

```bash
git add src/main/java/com/agentlibrary/domain
git commit -m "feat(domain): add Artifact, ArtifactMetadata, Filter, VersionRef, CommitResult, User"
```

### Task 4: `SemVer` comparator + validator

**Files:**
- Create: `src/main/java/com/agentlibrary/metadata/SemVer.java`
- Create: `src/test/java/com/agentlibrary/metadata/SemVerTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.agentlibrary.metadata;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SemVerTest {
    @Test
    void parse_valid() {
        SemVer v = SemVer.parse("1.2.3");
        assertThat(v.major()).isEqualTo(1);
        assertThat(v.minor()).isEqualTo(2);
        assertThat(v.patch()).isEqualTo(3);
    }

    @Test
    void parse_invalid_throws() {
        assertThatThrownBy(() -> SemVer.parse("1.2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemVer.parse("v1.2.3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemVer.parse("1.2.3.4")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compare_ordering() {
        assertThat(SemVer.parse("1.0.0")).isLessThan(SemVer.parse("1.0.1"));
        assertThat(SemVer.parse("1.1.0")).isGreaterThan(SemVer.parse("1.0.9"));
        assertThat(SemVer.parse("2.0.0")).isGreaterThan(SemVer.parse("1.999.999"));
    }

    @Test
    void matchesRange_caret() {
        assertThat(SemVer.matchesRange(SemVer.parse("1.2.3"), "^1.0")).isTrue();
        assertThat(SemVer.matchesRange(SemVer.parse("2.0.0"), "^1.0")).isFalse();
        assertThat(SemVer.matchesRange(SemVer.parse("1.0.0"), "^1.0")).isTrue();
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

Run: `./mvnw test -Dtest=SemVerTest`

- [ ] **Step 3: Implement `SemVer.java`**

```java
package com.agentlibrary.metadata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {
    private static final Pattern P = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    public static SemVer parse(String s) {
        Matcher m = P.matcher(s);
        if (!m.matches()) throw new IllegalArgumentException("Invalid semver: " + s);
        return new SemVer(
            Integer.parseInt(m.group(1)),
            Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(3))
        );
    }

    @Override
    public int compareTo(SemVer o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) return c;
        c = Integer.compare(minor, o.minor);
        if (c != 0) return c;
        return Integer.compare(patch, o.patch);
    }

    public String asString() { return major + "." + minor + "." + patch; }

    public static boolean matchesRange(SemVer v, String range) {
        if (range.startsWith("^")) {
            String base = range.substring(1);
            String[] parts = base.split("\\.");
            int majorReq = Integer.parseInt(parts[0]);
            int minorReq = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patchReq = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            SemVer lower = new SemVer(majorReq, minorReq, patchReq);
            SemVer upperExclusive = new SemVer(majorReq + 1, 0, 0);
            return v.compareTo(lower) >= 0 && v.compareTo(upperExclusive) < 0;
        }
        return v.equals(parse(range));
    }
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(metadata): add SemVer parser, comparator, caret-range matcher"
```

### Task 5: `MetadataCodec` — frontmatter + YAML round-trip

**Files:**
- Create: `src/main/java/com/agentlibrary/metadata/MetadataCodec.java`
- Create: `src/test/java/com/agentlibrary/metadata/MetadataCodecTest.java`

- [ ] **Step 1: Failing test**

```java
package com.agentlibrary.metadata;

import com.agentlibrary.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MetadataCodecTest {

    MetadataCodec codec = new MetadataCodec();

    @Test
    void splitFrontmatter_extractsYamlAndBody() {
        String source = """
            ---
            name: git-helper
            ---
            # Body
            hello
            """;
        MetadataCodec.Split s = codec.splitFrontmatter(source);
        assertThat(s.yaml()).contains("name: git-helper");
        assertThat(s.body()).startsWith("# Body");
    }

    @Test
    void splitFrontmatter_noFrontmatter_returnsEmptyYaml() {
        MetadataCodec.Split s = codec.splitFrontmatter("just body");
        assertThat(s.yaml()).isEmpty();
        assertThat(s.body()).isEqualTo("just body");
    }

    @Test
    void decode_fullMetadata() {
        String yaml = """
            name: git-helper
            title: Git Helper
            type: skill
            version: 1.2.0
            description: Helps with git
            harnesses: [claude, copilot]
            tags: [git, workflow]
            category: developer-tools
            language: en
            author: robert
            visibility: team
            created: '2026-04-19T10:00:00Z'
            updated: '2026-04-19T10:00:00Z'
            install:
              target: ~/.claude/skills/{name}/
              files: [skill.md]
              merge: null
            """;
        ArtifactMetadata m = codec.decode(yaml);
        assertThat(m.name()).isEqualTo("git-helper");
        assertThat(m.type()).isEqualTo(ArtifactType.SKILL);
        assertThat(m.version()).isEqualTo("1.2.0");
        assertThat(m.harnesses()).containsExactly(Harness.CLAUDE, Harness.COPILOT);
        assertThat(m.install().target()).isEqualTo("~/.claude/skills/{name}/");
        assertThat(m.visibility()).isEqualTo(Visibility.TEAM);
    }

    @Test
    void encode_roundTrip() {
        ArtifactMetadata m = new ArtifactMetadata(
            "git-helper", "Git Helper", ArtifactType.SKILL, "1.2.0", "desc",
            List.of(Harness.CLAUDE), List.of("git"), "dev", "en", "robert",
            Visibility.TEAM, Instant.parse("2026-04-19T10:00:00Z"), Instant.parse("2026-04-19T10:00:00Z"),
            new ArtifactMetadata.InstallHints("~/.claude/skills/{name}/", List.of("skill.md"), null),
            Map.of()
        );
        String yaml = codec.encode(m);
        ArtifactMetadata back = codec.decode(yaml);
        assertThat(back).isEqualTo(m);
    }

    @Test
    void assembleFrontmatter_writesDelimited() {
        String out = codec.assembleFrontmatter("name: x\n", "body line\n");
        assertThat(out).isEqualTo("---\nname: x\n---\nbody line\n");
    }
}
```

- [ ] **Step 2: Run — expect compile fail**

- [ ] **Step 3: Implement `MetadataCodec.java`**

```java
package com.agentlibrary.metadata;

import com.agentlibrary.domain.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.time.Instant;
import java.util.*;

public class MetadataCodec {

    public record Split(String yaml, String body) {}

    public Split splitFrontmatter(String source) {
        if (!source.startsWith("---\n") && !source.startsWith("---\r\n")) {
            return new Split("", source);
        }
        int start = source.indexOf('\n') + 1;
        int end = source.indexOf("\n---", start);
        if (end < 0) return new Split("", source);
        String yaml = source.substring(start, end);
        int bodyStart = end + 4; // skip "\n---"
        if (bodyStart < source.length() && source.charAt(bodyStart) == '\r') bodyStart++;
        if (bodyStart < source.length() && source.charAt(bodyStart) == '\n') bodyStart++;
        return new Split(yaml, source.substring(bodyStart));
    }

    public String assembleFrontmatter(String yaml, String body) {
        return "---\n" + yaml + (yaml.endsWith("\n") ? "" : "\n") + "---\n" + body;
    }

    @SuppressWarnings("unchecked")
    public ArtifactMetadata decode(String yaml) {
        Map<String, Object> m = new Yaml().load(yaml);
        String name = str(m, "name");
        String title = str(m, "title");
        ArtifactType type = ArtifactType.fromSlug(str(m, "type"));
        String version = str(m, "version");
        String description = strOrNull(m, "description");
        List<Harness> harnesses = ((List<String>) m.getOrDefault("harnesses", List.of()))
            .stream().map(Harness::fromSlug).toList();
        List<String> tags = (List<String>) m.getOrDefault("tags", List.of());
        String category = strOrNull(m, "category");
        String language = strOrNull(m, "language");
        String author = strOrNull(m, "author");
        Visibility visibility = Visibility.valueOf(
            strOrDefault(m, "visibility", "team").toUpperCase(Locale.ROOT));
        Instant created = parseInstant(m.get("created"));
        Instant updated = parseInstant(m.get("updated"));
        Map<String, Object> installMap = (Map<String, Object>) m.getOrDefault("install", Map.of());
        ArtifactMetadata.InstallHints install = new ArtifactMetadata.InstallHints(
            strOrNull(installMap, "target"),
            (List<String>) installMap.getOrDefault("files", List.of()),
            strOrNull(installMap, "merge")
        );
        Map<String, Object> ext = new LinkedHashMap<>();
        for (String k : m.keySet()) {
            if (!KNOWN.contains(k)) ext.put(k, m.get(k));
        }
        return new ArtifactMetadata(
            name, title, type, version, description, harnesses, tags, category,
            language, author, visibility, created, updated, install, ext
        );
    }

    public String encode(ArtifactMetadata meta) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("name", meta.name());
        m.put("title", meta.title());
        m.put("type", meta.type().slug());
        m.put("version", meta.version());
        if (meta.description() != null) m.put("description", meta.description());
        m.put("harnesses", meta.harnesses().stream().map(Harness::slug).toList());
        m.put("tags", meta.tags());
        if (meta.category() != null) m.put("category", meta.category());
        if (meta.language() != null) m.put("language", meta.language());
        if (meta.author() != null) m.put("author", meta.author());
        m.put("visibility", meta.visibility().name().toLowerCase(Locale.ROOT));
        if (meta.created() != null) m.put("created", meta.created().toString());
        if (meta.updated() != null) m.put("updated", meta.updated().toString());
        LinkedHashMap<String, Object> install = new LinkedHashMap<>();
        if (meta.install() != null) {
            install.put("target", meta.install().target());
            install.put("files", meta.install().files());
            install.put("merge", meta.install().merge());
            m.put("install", install);
        }
        m.putAll(meta.typeExtensions());
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        return new Yaml(opts).dump(m);
    }

    private static final Set<String> KNOWN = Set.of(
        "name", "title", "type", "version", "description", "harnesses", "tags",
        "category", "language", "author", "visibility", "created", "updated", "install"
    );

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) throw new IllegalArgumentException("Missing field: " + k);
        return v.toString();
    }
    private static String strOrNull(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }
    private static String strOrDefault(Map<String, Object> m, String k, String d) {
        Object v = m.get(k);
        return v == null ? d : v.toString();
    }
    private static Instant parseInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Date d) return d.toInstant();
        return Instant.parse(o.toString());
    }
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(metadata): frontmatter split + YAML encode/decode for ArtifactMetadata"
```

### Task 6: `MetadataValidator` (per-type schemas, semver enforcement, slug rules)

**Files:**
- Create: `src/main/java/com/agentlibrary/metadata/MetadataValidator.java`
- Create: `src/test/java/com/agentlibrary/metadata/MetadataValidatorTest.java`

- [ ] **Step 1: Failing test**

```java
package com.agentlibrary.metadata;

import com.agentlibrary.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MetadataValidatorTest {

    MetadataValidator validator = new MetadataValidator();

    ArtifactMetadata valid() {
        return new ArtifactMetadata(
            "git-helper", "Git Helper", ArtifactType.SKILL, "1.0.0", "d",
            List.of(Harness.CLAUDE), List.of(), "cat", "en", "robert",
            Visibility.TEAM, Instant.now(), Instant.now(),
            new ArtifactMetadata.InstallHints("~/.claude/skills/{name}/", List.of("skill.md"), null),
            Map.of()
        );
    }

    @Test
    void ok_whenValid() {
        validator.validate(valid());
    }

    @Test
    void rejects_invalidSlug() {
        ArtifactMetadata bad = new ArtifactMetadata(
            "Git Helper!", "Git", ArtifactType.SKILL, "1.0.0", "d",
            List.of(), List.of(), null, null, null, Visibility.TEAM,
            Instant.now(), Instant.now(),
            new ArtifactMetadata.InstallHints(null, List.of(), null), Map.of());
        assertThatThrownBy(() -> validator.validate(bad))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("name");
    }

    @Test
    void rejects_badSemver() {
        ArtifactMetadata bad = new ArtifactMetadata(
            "x", "X", ArtifactType.SKILL, "1.0", "d",
            List.of(), List.of(), null, null, null, Visibility.TEAM,
            Instant.now(), Instant.now(),
            new ArtifactMetadata.InstallHints(null, List.of(), null), Map.of());
        assertThatThrownBy(() -> validator.validate(bad))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("version");
    }

    @Test
    void rejects_versionNotGreater() {
        ArtifactMetadata m = valid();
        assertThatThrownBy(() -> validator.validateVersionBump(m, "1.0.0"))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must be greater");
    }
}
```

- [ ] **Step 2: Run — expect fail**

- [ ] **Step 3: Implement `ValidationException`**

Create: `src/main/java/com/agentlibrary/metadata/ValidationException.java`

```java
package com.agentlibrary.metadata;

public class ValidationException extends RuntimeException {
    public ValidationException(String msg) { super(msg); }
}
```

- [ ] **Step 4: Implement `MetadataValidator.java`**

```java
package com.agentlibrary.metadata;

import com.agentlibrary.domain.ArtifactMetadata;
import com.agentlibrary.domain.ArtifactType;

import java.util.regex.Pattern;

public class MetadataValidator {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    public void validate(ArtifactMetadata m) {
        if (m.name() == null || !SLUG.matcher(m.name()).matches())
            throw new ValidationException("name must be lowercase slug: " + m.name());
        if (m.title() == null || m.title().isBlank())
            throw new ValidationException("title required");
        if (m.type() == null)
            throw new ValidationException("type required");
        try { SemVer.parse(m.version()); }
        catch (Exception e) { throw new ValidationException("version must be semver: " + m.version()); }
        if (m.harnesses() == null)
            throw new ValidationException("harnesses required (may be empty list)");
        if (m.type() == ArtifactType.MCP) {
            if (!m.typeExtensions().containsKey("serverName"))
                throw new ValidationException("mcp.serverName required");
        }
        if (m.type() == ArtifactType.HOOK) {
            if (!m.typeExtensions().containsKey("event"))
                throw new ValidationException("hook.event required");
        }
    }

    public void validateVersionBump(ArtifactMetadata incoming, String existingVersion) {
        SemVer incomingV = SemVer.parse(incoming.version());
        SemVer existingV = SemVer.parse(existingVersion);
        if (incomingV.compareTo(existingV) <= 0) {
            throw new ValidationException(
                "version " + incoming.version() + " must be greater than " + existingVersion);
        }
    }
}
```

- [ ] **Step 5: Run — PASS**

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(metadata): add MetadataValidator with slug, semver, per-type rules"
```

---

## Milestone 3 — Git Storage Layer

### Task 7: `ArtifactRepository` interface + `WriteQueue`

**Files:**
- Create: `src/main/java/com/agentlibrary/storage/ArtifactRepository.java`
- Create: `src/main/java/com/agentlibrary/storage/WriteQueue.java`
- Create: `src/test/java/com/agentlibrary/storage/WriteQueueTest.java`

- [ ] **Step 1: Failing test for WriteQueue serialisation**

```java
package com.agentlibrary.storage;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.*;

class WriteQueueTest {
    @Test
    void runsTasksSerially() throws Exception {
        WriteQueue q = new WriteQueue();
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxSeen = new AtomicInteger();
        int n = 20;
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            q.submit(() -> {
                int c = concurrent.incrementAndGet();
                maxSeen.updateAndGet(x -> Math.max(x, c));
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                concurrent.decrementAndGet();
                done.countDown();
                return null;
            });
        }
        done.await();
        assertThat(maxSeen.get()).isEqualTo(1);
        q.shutdown();
    }

    @Test
    void propagatesReturnValue() throws Exception {
        WriteQueue q = new WriteQueue();
        String v = q.submit(() -> "hello").get();
        assertThat(v).isEqualTo("hello");
        q.shutdown();
    }
}
```

- [ ] **Step 2: Run — fail**

- [ ] **Step 3: Implement `WriteQueue.java`**

```java
package com.agentlibrary.storage;

import java.util.concurrent.*;

public class WriteQueue {
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "artifact-write-queue");
        t.setDaemon(true);
        return t;
    });

    public <T> Future<T> submit(Callable<T> task) {
        return exec.submit(task);
    }

    public void shutdown() {
        exec.shutdown();
        try { exec.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Implement `ArtifactRepository.java`**

```java
package com.agentlibrary.storage;

import com.agentlibrary.domain.*;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface ArtifactRepository {
    List<ArtifactMetadata> list(Filter f);
    Optional<Artifact> get(ArtifactType type, String name, String ref);
    CommitResult save(Artifact a, String message, User u, boolean force);
    void delete(ArtifactType type, String name, String message, User u);
    List<VersionRef> versions(ArtifactType type, String name);
    InputStream bundle(ArtifactType type, String name, String ref);
}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/agentlibrary/storage src/test/java/com/agentlibrary/storage
git commit -m "feat(storage): add ArtifactRepository interface and WriteQueue"
```

### Task 8: `IndexFile` — read/write `INDEX.yaml`

**Files:**
- Create: `src/main/java/com/agentlibrary/storage/IndexFile.java`
- Create: `src/test/java/com/agentlibrary/storage/IndexFileTest.java`

- [ ] **Step 1: Failing test**

```java
package com.agentlibrary.storage;

import com.agentlibrary.domain.*;
import com.agentlibrary.metadata.MetadataCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class IndexFileTest {

    MetadataCodec codec = new MetadataCodec();
    IndexFile indexFile = new IndexFile(codec);

    @Test
    void roundTrip_preservesEntries() {
        ArtifactMetadata a = new ArtifactMetadata(
            "foo", "Foo", ArtifactType.SKILL, "1.0.0", "desc",
            List.of(Harness.CLAUDE), List.of("t"), "c", "en", "me",
            Visibility.TEAM, Instant.parse("2026-04-19T10:00:00Z"),
            Instant.parse("2026-04-19T10:00:00Z"),
            new ArtifactMetadata.InstallHints("~/", List.of("skill.md"), null),
            Map.of());
        String encoded = indexFile.write(List.of(a));
        List<ArtifactMetadata> decoded = indexFile.read(encoded);
        assertThat(decoded).containsExactly(a);
    }

    @Test
    void read_empty_returnsEmptyList() {
        assertThat(indexFile.read("artifacts: []\n")).isEmpty();
        assertThat(indexFile.read("")).isEmpty();
    }
}
```

- [ ] **Step 2: Run — compile fail**

- [ ] **Step 3: Implement `IndexFile.java`**

```java
package com.agentlibrary.storage;

import com.agentlibrary.domain.ArtifactMetadata;
import com.agentlibrary.metadata.MetadataCodec;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

public class IndexFile {

    private final MetadataCodec codec;

    public IndexFile(MetadataCodec codec) { this.codec = codec; }

    @SuppressWarnings("unchecked")
    public List<ArtifactMetadata> read(String yamlSource) {
        if (yamlSource == null || yamlSource.isBlank()) return List.of();
        Map<String, Object> root = new Yaml().load(yamlSource);
        if (root == null) return List.of();
        List<Map<String, Object>> entries = (List<Map<String, Object>>) root.getOrDefault("artifacts", List.of());
        List<ArtifactMetadata> out = new ArrayList<>();
        for (Map<String, Object> e : entries) {
            String asYaml = new Yaml().dump(e);
            out.add(codec.decode(asYaml));
        }
        return out;
    }

    public String write(List<ArtifactMetadata> entries) {
        List<Object> yamlEntries = new ArrayList<>();
        for (ArtifactMetadata m : entries) {
            Map<String, Object> parsed = new Yaml().load(codec.encode(m));
            yamlEntries.add(parsed);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("artifacts", yamlEntries);
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(opts).dump(root);
    }
}
```

- [ ] **Step 4: PASS, commit**

```bash
git commit -am "feat(storage): INDEX.yaml codec (IndexFile)"
```

### Task 9: `LocalGitArtifactRepository` — skeleton + init

**Files:**
- Create: `src/main/java/com/agentlibrary/storage/LocalGitArtifactRepository.java`
- Create: `src/test/java/com/agentlibrary/storage/LocalGitArtifactRepositoryTest.java`

- [ ] **Step 1: Failing test — initialises empty bare repo on first call**

```java
package com.agentlibrary.storage;

import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.metadata.MetadataValidator;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class LocalGitArtifactRepositoryTest {

    @Test
    void initialises_bareRepoOnConstruction(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("lib.git");
        Path work = tmp.resolve("work");
        new LocalGitArtifactRepository(
            repo, work, new MetadataCodec(), new MetadataValidator(),
            new IndexFile(new MetadataCodec()), new WriteQueue(),
            "server", "server@localhost");
        assertThat(repo).isDirectory();
        try (Git g = Git.open(repo.toFile())) {
            assertThat(g.getRepository().isBare()).isTrue();
        }
    }
}
```

- [ ] **Step 2: Compile fail**

- [ ] **Step 3: Implement constructor only**

```java
package com.agentlibrary.storage;

import com.agentlibrary.domain.*;
import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.metadata.MetadataValidator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public class LocalGitArtifactRepository implements ArtifactRepository {

    private final Path bareRepo;
    private final Path workTree;
    private final MetadataCodec codec;
    private final MetadataValidator validator;
    private final IndexFile indexFile;
    private final WriteQueue queue;
    private final String serverName;
    private final String serverEmail;

    public LocalGitArtifactRepository(Path bareRepo, Path workTree,
                                      MetadataCodec codec, MetadataValidator validator,
                                      IndexFile indexFile, WriteQueue queue,
                                      String serverName, String serverEmail) {
        this.bareRepo = bareRepo;
        this.workTree = workTree;
        this.codec = codec;
        this.validator = validator;
        this.indexFile = indexFile;
        this.queue = queue;
        this.serverName = serverName;
        this.serverEmail = serverEmail;
        try {
            initIfMissing();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise repo", e);
        }
    }

    private void initIfMissing() throws IOException, org.eclipse.jgit.api.errors.GitAPIException {
        if (Files.isDirectory(bareRepo) && Files.exists(bareRepo.resolve("HEAD"))) return;
        Files.createDirectories(bareRepo);
        Git.init().setBare(true).setDirectory(bareRepo.toFile()).call().close();
        Files.createDirectories(workTree);
    }

    Repository openRepo() throws IOException {
        return new FileRepositoryBuilder().setGitDir(bareRepo.toFile()).build();
    }

    @Override public List<ArtifactMetadata> list(Filter f) { throw new UnsupportedOperationException(); }
    @Override public Optional<Artifact> get(ArtifactType t, String n, String r) { throw new UnsupportedOperationException(); }
    @Override public CommitResult save(Artifact a, String m, User u, boolean force) { throw new UnsupportedOperationException(); }
    @Override public void delete(ArtifactType t, String n, String m, User u) { throw new UnsupportedOperationException(); }
    @Override public List<VersionRef> versions(ArtifactType t, String n) { throw new UnsupportedOperationException(); }
    @Override public InputStream bundle(ArtifactType t, String n, String r) { throw new UnsupportedOperationException(); }
}
```

- [ ] **Step 4: PASS**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(storage): LocalGitArtifactRepository init with bare repo bootstrap"
```

### Task 10: `save` — write artifact, commit, tag

**Files:**
- Modify: `src/main/java/com/agentlibrary/storage/LocalGitArtifactRepository.java`
- Modify: `src/test/java/com/agentlibrary/storage/LocalGitArtifactRepositoryTest.java`

- [ ] **Step 1: Failing test — save creates commit + tag + INDEX**

```java
@Test
void save_createsFilesCommitAndTag(@TempDir Path tmp) throws Exception {
    LocalGitArtifactRepository repo = newRepo(tmp);
    ArtifactMetadata meta = sample("foo", "1.0.0");
    Artifact a = new Artifact(meta, "# Body\n", Map.of());
    CommitResult r = repo.save(a, "initial", new User("robert", "", Set.of("USER")), false);
    assertThat(r.commitSha()).isNotBlank();
    assertThat(r.tagName()).isEqualTo("skills/foo@1.0.0");

    try (Git g = Git.open(repo.bareRepoPath().toFile())) {
        List<RevTag> tags = g.tagList().call().stream()
            .map(ref -> readTag(g.getRepository(), ref))
            .filter(java.util.Objects::nonNull).toList();
        // lightweight tags show as Ref not RevTag; just assert name exists
        assertThat(g.tagList().call().stream().map(Ref::getName))
            .anyMatch(n -> n.equals("refs/tags/skills/foo@1.0.0"));
    }
}
```

(Also add helper methods `newRepo`, `sample`, and expose `bareRepoPath()` on the repo class for tests — see Step 3.)

- [ ] **Step 2: Run — fail (UnsupportedOperationException)**

- [ ] **Step 3: Implement `save` + helpers**

Replace the UnsupportedOp stub for `save` with:

```java
@Override
public CommitResult save(Artifact a, String message, User user, boolean force) {
    validator.validate(a.metadata());
    try {
        return queue.submit(() -> doSave(a, message, user, force)).get();
    } catch (Exception e) {
        if (e.getCause() instanceof RuntimeException r) throw r;
        throw new RuntimeException(e);
    }
}

private CommitResult doSave(Artifact a, String message, User user, boolean force) throws Exception {
    ArtifactType type = a.metadata().type();
    String name = a.metadata().name();
    Path dir = workTree.resolve(type.folder()).resolve(name);

    // Check existing version
    Optional<Artifact> existing = doGet(type, name, "HEAD");
    if (existing.isPresent() && !force) {
        validator.validateVersionBump(a.metadata(), existing.get().metadata().version());
    }

    // Ensure working tree exists & is up to date
    syncWorkTreeToHead();

    // Wipe dir and rewrite
    if (Files.isDirectory(dir)) deleteRecursive(dir);
    Files.createDirectories(dir);
    writeArtifactFiles(dir, a);

    // Rewrite INDEX.yaml
    List<ArtifactMetadata> all = collectAllMetadata(workTree);
    Files.writeString(workTree.resolve("INDEX.yaml"), indexFile.write(all));

    // Commit
    try (Git g = Git.wrap(openWorkRepo())) {
        g.add().addFilepattern(".").call();
        String commitMsg = message == null || message.isBlank()
            ? type.slug() + "/" + name + ": update by " + user.username()
            : message;
        var commit = g.commit()
            .setAuthor(user.username(), user.username() + "@agent-library")
            .setCommitter(serverName, serverEmail)
            .setMessage(commitMsg)
            .call();

        String tagName = type.folder() + "/" + name + "@" + a.metadata().version();
        g.tag().setName(tagName).setObjectId(commit).call();
        pushWorkToBare(g);
        return new CommitResult(commit.getName(), tagName, existing.isEmpty());
    }
}
```

- [ ] **Step 4: Add support methods**

Also add to the class:

```java
public Path bareRepoPath() { return bareRepo; }
public Path workTreePath() { return workTree; }

private void syncWorkTreeToHead() throws Exception {
    if (!Files.exists(workTree.resolve(".git"))) {
        Files.createDirectories(workTree);
        Git.cloneRepository()
            .setURI(bareRepo.toUri().toString())
            .setDirectory(workTree.toFile())
            .call()
            .close();
    } else {
        try (Git g = Git.open(workTree.toFile())) {
            try { g.pull().call(); } catch (Exception ignored) { /* empty repo: no upstream yet */ }
        }
    }
}

private org.eclipse.jgit.lib.Repository openWorkRepo() throws IOException {
    return new FileRepositoryBuilder().setWorkTree(workTree.toFile()).build();
}

private void pushWorkToBare(Git g) throws Exception {
    g.push().setRemote(bareRepo.toUri().toString()).setPushAll().setPushTags().call();
}

private void writeArtifactFiles(Path dir, Artifact a) throws IOException {
    ArtifactType type = a.metadata().type();
    String primaryName = type.primaryFile();
    String yaml = codec.encode(a.metadata());
    String fileContent;
    if (type.hasSidecar()) {
        Files.writeString(dir.resolve("meta.yaml"), yaml);
        fileContent = a.primaryContent();
    } else {
        fileContent = codec.assembleFrontmatter(yaml, a.primaryContent());
    }
    Files.writeString(dir.resolve(primaryName), fileContent);
    for (var e : a.extraFiles().entrySet()) {
        Path target = dir.resolve(e.getKey());
        Files.createDirectories(target.getParent());
        Files.write(target, e.getValue());
    }
}

private List<ArtifactMetadata> collectAllMetadata(Path root) throws IOException {
    List<ArtifactMetadata> out = new ArrayList<>();
    for (ArtifactType type : ArtifactType.values()) {
        Path folder = root.resolve(type.folder());
        if (!Files.isDirectory(folder)) continue;
        try (var stream = Files.list(folder)) {
            for (Path entry : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(entry)) continue;
                ArtifactMetadata m = loadMetadata(type, entry);
                if (m != null) out.add(m);
            }
        }
    }
    out.sort(Comparator.comparing(ArtifactMetadata::type).thenComparing(ArtifactMetadata::name));
    return out;
}

private ArtifactMetadata loadMetadata(ArtifactType type, Path dir) throws IOException {
    Path primary = dir.resolve(type.primaryFile());
    if (!Files.isRegularFile(primary)) return null;
    if (type.hasSidecar()) {
        Path meta = dir.resolve("meta.yaml");
        if (!Files.isRegularFile(meta)) return null;
        return codec.decode(Files.readString(meta));
    } else {
        String source = Files.readString(primary);
        MetadataCodec.Split split = codec.splitFrontmatter(source);
        if (split.yaml().isEmpty()) return null;
        return codec.decode(split.yaml());
    }
}

private static void deleteRecursive(Path p) throws IOException {
    if (!Files.exists(p)) return;
    try (var walk = Files.walk(p)) {
        walk.sorted(Comparator.reverseOrder()).forEach(x -> {
            try { Files.delete(x); } catch (IOException ignored) {}
        });
    }
}
```

- [ ] **Step 5: Implement `doGet` (used above)**

```java
private Optional<Artifact> doGet(ArtifactType type, String name, String ref) throws Exception {
    // Walk the given ref's tree; load primary + optional sidecar + extraFiles
    try (var repo = openRepo()) {
        var head = repo.resolve(ref == null ? "HEAD" : ref);
        if (head == null) return Optional.empty();
        try (var walk = new org.eclipse.jgit.revwalk.RevWalk(repo)) {
            var commit = walk.parseCommit(head);
            var tree = commit.getTree();
            String base = type.folder() + "/" + name + "/";
            Map<String, byte[]> files = new LinkedHashMap<>();
            try (var tw = new org.eclipse.jgit.treewalk.TreeWalk(repo)) {
                tw.addTree(tree);
                tw.setRecursive(true);
                while (tw.next()) {
                    String path = tw.getPathString();
                    if (path.startsWith(base)) {
                        var loader = repo.open(tw.getObjectId(0));
                        files.put(path.substring(base.length()), loader.getBytes());
                    }
                }
            }
            if (files.isEmpty()) return Optional.empty();
            byte[] primaryBytes = files.remove(type.primaryFile());
            if (primaryBytes == null) return Optional.empty();
            ArtifactMetadata meta;
            String primaryContent;
            if (type.hasSidecar()) {
                byte[] sidecar = files.remove("meta.yaml");
                if (sidecar == null) return Optional.empty();
                meta = codec.decode(new String(sidecar));
                primaryContent = new String(primaryBytes);
            } else {
                MetadataCodec.Split split = codec.splitFrontmatter(new String(primaryBytes));
                meta = codec.decode(split.yaml());
                primaryContent = split.body();
            }
            return Optional.of(new Artifact(meta, primaryContent, files));
        }
    }
}

@Override
public Optional<Artifact> get(ArtifactType type, String name, String ref) {
    try { return doGet(type, name, ref); }
    catch (Exception e) { throw new RuntimeException(e); }
}
```

- [ ] **Step 6: Test helpers in `LocalGitArtifactRepositoryTest`**

```java
private LocalGitArtifactRepository newRepo(Path tmp) {
    return new LocalGitArtifactRepository(
        tmp.resolve("lib.git"), tmp.resolve("work"),
        new MetadataCodec(), new MetadataValidator(),
        new IndexFile(new MetadataCodec()), new WriteQueue(),
        "server", "server@localhost");
}

private ArtifactMetadata sample(String name, String version) {
    return new ArtifactMetadata(
        name, "Title", ArtifactType.SKILL, version, "desc",
        List.of(Harness.CLAUDE), List.of("t"), "cat", "en", "robert",
        Visibility.TEAM, java.time.Instant.parse("2026-04-19T10:00:00Z"),
        java.time.Instant.parse("2026-04-19T10:00:00Z"),
        new ArtifactMetadata.InstallHints("~/.claude/skills/{name}/", List.of("skill.md"), null),
        java.util.Map.of());
}
```

- [ ] **Step 7: Run test — PASS**

Run: `./mvnw test -Dtest=LocalGitArtifactRepositoryTest`

- [ ] **Step 8: Commit**

```bash
git commit -am "feat(storage): LocalGitArtifactRepository.save creates files, commit, tag"
```

### Task 11: `list`, `versions`, `delete`, `bundle`

**Files:**
- Modify: `src/main/java/com/agentlibrary/storage/LocalGitArtifactRepository.java`
- Modify: `src/test/java/com/agentlibrary/storage/LocalGitArtifactRepositoryTest.java`

- [ ] **Step 1: Failing tests for list / versions / delete / bundle**

```java
@Test
void list_returnsAllSaved(@TempDir Path tmp) throws Exception {
    var repo = newRepo(tmp);
    var user = new User("u", "", Set.of("USER"));
    repo.save(new Artifact(sample("a", "1.0.0"), "body", Map.of()), "init", user, false);
    repo.save(new Artifact(sample("b", "1.0.0"), "body", Map.of()), "init", user, false);
    var metas = repo.list(Filter.empty());
    assertThat(metas).extracting(ArtifactMetadata::name).containsExactlyInAnyOrder("a", "b");
}

@Test
void versions_listsTagsForArtifact(@TempDir Path tmp) throws Exception {
    var repo = newRepo(tmp);
    var user = new User("u", "", Set.of("USER"));
    repo.save(new Artifact(sample("a", "1.0.0"), "body", Map.of()), "init", user, false);
    repo.save(new Artifact(sample("a", "1.1.0"), "body", Map.of()), "bump", user, false);
    var vs = repo.versions(ArtifactType.SKILL, "a");
    assertThat(vs).extracting(VersionRef::version).containsExactly("1.1.0", "1.0.0");
}

@Test
void delete_removesFolder(@TempDir Path tmp) throws Exception {
    var repo = newRepo(tmp);
    var user = new User("u", "", Set.of("USER"));
    repo.save(new Artifact(sample("a", "1.0.0"), "body", Map.of()), "init", user, false);
    repo.delete(ArtifactType.SKILL, "a", "remove", user);
    assertThat(repo.get(ArtifactType.SKILL, "a", "HEAD")).isEmpty();
}

@Test
void bundle_returnsTarGzOfArtifactDir(@TempDir Path tmp) throws Exception {
    var repo = newRepo(tmp);
    var user = new User("u", "", Set.of("USER"));
    repo.save(new Artifact(sample("a", "1.0.0"), "body", Map.of("extra.txt", "x".getBytes())), "init", user, false);
    try (var in = repo.bundle(ArtifactType.SKILL, "a", "HEAD")) {
        assertThat(in.readAllBytes().length).isGreaterThan(0);
    }
}
```

- [ ] **Step 2: Run — fail**

- [ ] **Step 3: Implement `list`**

```java
@Override
public List<ArtifactMetadata> list(Filter f) {
    try (var repo = openRepo()) {
        var head = repo.resolve("HEAD");
        if (head == null) return List.of();
        try (var rw = new org.eclipse.jgit.revwalk.RevWalk(repo)) {
            var tree = rw.parseCommit(head).getTree();
            try (var tw = new org.eclipse.jgit.treewalk.TreeWalk(repo)) {
                tw.addTree(tree);
                tw.setRecursive(true);
                for (var t : org.eclipse.jgit.treewalk.filter.PathFilter.ALL_PATH_FILTERS) {} // placeholder
                byte[] indexBytes = null;
                while (tw.next()) {
                    if (tw.getPathString().equals("INDEX.yaml")) {
                        indexBytes = repo.open(tw.getObjectId(0)).getBytes();
                        break;
                    }
                }
                if (indexBytes == null) return List.of();
                return indexFile.read(new String(indexBytes)).stream()
                    .filter(m -> matches(m, f))
                    .toList();
            }
        }
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

private boolean matches(ArtifactMetadata m, Filter f) {
    if (f.type() != null && m.type() != f.type()) return false;
    if (f.harness() != null && !m.harnesses().contains(f.harness())) return false;
    if (!f.tags().isEmpty() && m.tags().stream().noneMatch(f.tags()::contains)) return false;
    if (f.category() != null && !f.category().equals(m.category())) return false;
    if (f.language() != null && !f.language().equals(m.language())) return false;
    if (f.author() != null && !f.author().equals(m.author())) return false;
    if (f.query() != null && !f.query().isBlank()) {
        String q = f.query().toLowerCase(Locale.ROOT);
        String hay = (m.title() + " " + (m.description() == null ? "" : m.description())).toLowerCase(Locale.ROOT);
        if (!hay.contains(q)) return false;
    }
    return true;
}
```

- [ ] **Step 4: Implement `versions`**

```java
@Override
public List<VersionRef> versions(ArtifactType type, String name) {
    String prefix = "refs/tags/" + type.folder() + "/" + name + "@";
    try (var g = Git.wrap(openRepo())) {
        List<VersionRef> refs = new ArrayList<>();
        for (var ref : g.tagList().call()) {
            String full = ref.getName();
            if (!full.startsWith(prefix)) continue;
            String version = full.substring(prefix.length());
            String sha = ref.getObjectId().getName();
            refs.add(new VersionRef(version, sha, java.time.Instant.EPOCH));
        }
        refs.sort((x, y) -> com.agentlibrary.metadata.SemVer.parse(y.version())
            .compareTo(com.agentlibrary.metadata.SemVer.parse(x.version())));
        return refs;
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
```

- [ ] **Step 5: Implement `delete`**

```java
@Override
public void delete(ArtifactType type, String name, String message, User user) {
    try {
        queue.submit(() -> { doDelete(type, name, message, user); return null; }).get();
    } catch (Exception e) {
        throw new RuntimeException(e.getCause());
    }
}

private void doDelete(ArtifactType type, String name, String message, User user) throws Exception {
    syncWorkTreeToHead();
    Path dir = workTree.resolve(type.folder()).resolve(name);
    if (!Files.isDirectory(dir)) return;
    deleteRecursive(dir);
    List<ArtifactMetadata> all = collectAllMetadata(workTree);
    Files.writeString(workTree.resolve("INDEX.yaml"), indexFile.write(all));
    try (Git g = Git.wrap(openWorkRepo())) {
        g.add().addFilepattern(".").call();
        g.rm().addFilepattern(type.folder() + "/" + name).call();
        g.commit()
            .setAuthor(user.username(), user.username() + "@agent-library")
            .setCommitter(serverName, serverEmail)
            .setMessage(message == null || message.isBlank()
                ? type.slug() + "/" + name + ": delete by " + user.username()
                : message)
            .call();
        pushWorkToBare(g);
    }
}
```

- [ ] **Step 6: Implement `bundle` (tar.gz of the folder at ref)**

```java
@Override
public InputStream bundle(ArtifactType type, String name, String ref) {
    try {
        var artifact = doGet(type, name, ref).orElseThrow(
            () -> new IllegalArgumentException("Not found: " + type.slug() + "/" + name));
        var baos = new java.io.ByteArrayOutputStream();
        try (var gz = new java.util.zip.GZIPOutputStream(baos);
             var tar = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(gz)) {
            // Note: adds commons-compress dependency — see Step 7
            writeTarEntry(tar, type.primaryFile(),
                type.hasSidecar() ? artifact.primaryContent().getBytes()
                                  : codec.assembleFrontmatter(codec.encode(artifact.metadata()),
                                                              artifact.primaryContent()).getBytes());
            if (type.hasSidecar()) {
                writeTarEntry(tar, "meta.yaml", codec.encode(artifact.metadata()).getBytes());
            }
            for (var e : artifact.extraFiles().entrySet()) {
                writeTarEntry(tar, e.getKey(), e.getValue());
            }
            tar.finish();
        }
        return new java.io.ByteArrayInputStream(baos.toByteArray());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

private static void writeTarEntry(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar,
                                  String name, byte[] data) throws IOException {
    var entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(name);
    entry.setSize(data.length);
    tar.putArchiveEntry(entry);
    tar.write(data);
    tar.closeArchiveEntry();
}
```

- [ ] **Step 7: Add `commons-compress` to `pom.xml`**

Inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-compress</artifactId>
    <version>1.27.1</version>
</dependency>
```

- [ ] **Step 8: Run — PASS**

Run: `./mvnw test -Dtest=LocalGitArtifactRepositoryTest`

- [ ] **Step 9: Commit**

```bash
git commit -am "feat(storage): list, versions, delete, bundle"
```

---

## Milestone 4 — Index + Search

### Task 12: `IndexService` — in-memory cache refreshed on commit

**Files:**
- Create: `src/main/java/com/agentlibrary/index/IndexService.java`
- Create: `src/test/java/com/agentlibrary/index/IndexServiceTest.java`

- [ ] **Step 1: Test**

```java
package com.agentlibrary.index;

import com.agentlibrary.domain.*;
import com.agentlibrary.storage.*;
import com.agentlibrary.metadata.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class IndexServiceTest {
    @Test
    void reflectsCurrentRepoState(@TempDir Path tmp) throws Exception {
        var codec = new MetadataCodec();
        var repo = new LocalGitArtifactRepository(
            tmp.resolve("lib.git"), tmp.resolve("work"),
            codec, new MetadataValidator(), new IndexFile(codec), new WriteQueue(),
            "s", "s@l");
        var user = new User("u", "", Set.of("USER"));
        repo.save(new Artifact(new ArtifactMetadata(
            "foo", "Foo", ArtifactType.SKILL, "1.0.0", "d",
            List.of(Harness.CLAUDE), List.of(), "c", "en", "u",
            Visibility.TEAM, java.time.Instant.now(), java.time.Instant.now(),
            new ArtifactMetadata.InstallHints("~/", List.of("skill.md"), null),
            Map.of()
        ), "body", Map.of()), "init", user, false);

        var service = new IndexService(repo);
        service.refresh();
        assertThat(service.snapshot()).hasSize(1);
    }
}
```

- [ ] **Step 2: Implement**

```java
package com.agentlibrary.index;

import com.agentlibrary.domain.*;
import com.agentlibrary.storage.ArtifactRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IndexService {

    private final ArtifactRepository repo;
    private final AtomicReference<List<ArtifactMetadata>> cache = new AtomicReference<>(List.of());

    public IndexService(ArtifactRepository repo) { this.repo = repo; }

    public synchronized void refresh() {
        cache.set(repo.list(Filter.empty()));
    }

    public List<ArtifactMetadata> snapshot() { return cache.get(); }
}
```

- [ ] **Step 3: PASS + commit**

```bash
git commit -am "feat(index): IndexService with in-memory snapshot"
```

### Task 13: `SearchService` with Lucene RAMDirectory

**Files:**
- Create: `src/main/java/com/agentlibrary/index/SearchService.java`
- Create: `src/test/java/com/agentlibrary/index/SearchServiceTest.java`

- [ ] **Step 1: Failing test**

```java
package com.agentlibrary.index;

import com.agentlibrary.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SearchServiceTest {
    @Test
    void findsByTitleAndDescription() throws Exception {
        var svc = new SearchService();
        svc.reindex(List.of(
            meta("alpha", "Alpha Tool", "installs alpha"),
            meta("beta", "Beta Helper", "configures beta")
        ));
        assertThat(svc.search("alpha").stream().map(ArtifactMetadata::name))
            .containsExactly("alpha");
        assertThat(svc.search("configur").stream().map(ArtifactMetadata::name))
            .containsExactly("beta");
    }

    private ArtifactMetadata meta(String name, String title, String desc) {
        return new ArtifactMetadata(name, title, ArtifactType.SKILL, "1.0.0", desc,
            List.of(), List.of(), null, null, null, Visibility.TEAM,
            Instant.now(), Instant.now(),
            new ArtifactMetadata.InstallHints(null, List.of(), null), Map.of());
    }
}
```

- [ ] **Step 2: Implement `SearchService.java`**

```java
package com.agentlibrary.index;

import com.agentlibrary.domain.ArtifactMetadata;
import com.agentlibrary.domain.ArtifactType;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SearchService {

    private volatile Directory dir = new ByteBuffersDirectory();
    private volatile Map<String, ArtifactMetadata> lookup = Map.of();

    public synchronized void reindex(List<ArtifactMetadata> items) throws Exception {
        Directory d = new ByteBuffersDirectory();
        try (IndexWriter w = new IndexWriter(d, new IndexWriterConfig(new StandardAnalyzer()))) {
            for (var m : items) {
                Document doc = new Document();
                String id = m.type().slug() + ":" + m.name();
                doc.add(new StringField("id", id, Field.Store.YES));
                doc.add(new TextField("title", nullToEmpty(m.title()), Field.Store.NO));
                doc.add(new TextField("description", nullToEmpty(m.description()), Field.Store.NO));
                doc.add(new TextField("tags", String.join(" ", m.tags()), Field.Store.NO));
                w.addDocument(doc);
            }
        }
        Map<String, ArtifactMetadata> newLookup = new HashMap<>();
        for (var m : items) newLookup.put(m.type().slug() + ":" + m.name(), m);
        this.dir = d;
        this.lookup = newLookup;
    }

    public List<ArtifactMetadata> search(String queryText) throws Exception {
        if (queryText == null || queryText.isBlank()) return List.copyOf(lookup.values());
        QueryParser parser = new MultiFieldQueryParser(
            new String[] { "title", "description", "tags" }, new StandardAnalyzer());
        Query q = parser.parse(QueryParser.escape(queryText) + "*");
        try (IndexReader r = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(r);
            TopDocs top = searcher.search(q, 200);
            List<ArtifactMetadata> out = new ArrayList<>();
            for (var sd : top.scoreDocs) {
                String id = searcher.storedFields().document(sd.doc).get("id");
                ArtifactMetadata m = lookup.get(id);
                if (m != null) out.add(m);
            }
            return out;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
```

- [ ] **Step 3: PASS, commit**

```bash
git commit -am "feat(index): Lucene-backed SearchService (RAMDirectory)"
```

### Task 14: Wire `IndexService` to rebuild `SearchService` on refresh

**Files:**
- Modify: `src/main/java/com/agentlibrary/index/IndexService.java`

- [ ] **Step 1: Modify `IndexService` to drive SearchService**

```java
@Service
public class IndexService {
    private final ArtifactRepository repo;
    private final SearchService search;
    private final AtomicReference<List<ArtifactMetadata>> cache = new AtomicReference<>(List.of());

    public IndexService(ArtifactRepository repo, SearchService search) {
        this.repo = repo;
        this.search = search;
    }

    public synchronized void refresh() {
        var items = repo.list(Filter.empty());
        cache.set(items);
        try { search.reindex(items); } catch (Exception e) { throw new RuntimeException(e); }
    }

    public List<ArtifactMetadata> snapshot() { return cache.get(); }
}
```

- [ ] **Step 2: Update `IndexServiceTest`** — construct with a `SearchService()`.

- [ ] **Step 3: Run + commit**

```bash
git commit -am "refactor(index): IndexService drives SearchService reindex on refresh"
```

---

## Milestone 5 — ArtifactService (orchestration) + REST API

### Task 15: `ArtifactService`

**Files:**
- Create: `src/main/java/com/agentlibrary/service/ArtifactService.java`
- Create: `src/test/java/com/agentlibrary/service/ArtifactServiceTest.java`

- [ ] **Step 1: Write behaviour test (save triggers index refresh)**

```java
package com.agentlibrary.service;

import com.agentlibrary.domain.*;
import com.agentlibrary.index.IndexService;
import com.agentlibrary.storage.ArtifactRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class ArtifactServiceTest {
    @Test
    void save_thenRefreshesIndex() {
        ArtifactRepository repo = mock(ArtifactRepository.class);
        when(repo.save(any(), any(), any(), anyBoolean()))
            .thenReturn(new CommitResult("abc", "skills/x@1.0.0", true));
        IndexService idx = mock(IndexService.class);
        var svc = new ArtifactService(repo, idx);
        svc.save(new Artifact(fake(), "b", Map.of()), "m", new User("u","",java.util.Set.of("USER")), false);
        verify(idx).refresh();
    }

    private ArtifactMetadata fake() {
        return new ArtifactMetadata("x", "X", ArtifactType.SKILL, "1.0.0", "d",
            List.of(), List.of(), null, null, null, Visibility.TEAM,
            java.time.Instant.now(), java.time.Instant.now(),
            new ArtifactMetadata.InstallHints(null, List.of(), null), Map.of());
    }
}
```

- [ ] **Step 2: Implement**

```java
package com.agentlibrary.service;

import com.agentlibrary.domain.*;
import com.agentlibrary.index.IndexService;
import com.agentlibrary.storage.ArtifactRepository;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Service
public class ArtifactService {
    private final ArtifactRepository repo;
    private final IndexService index;

    public ArtifactService(ArtifactRepository repo, IndexService index) {
        this.repo = repo;
        this.index = index;
    }

    public CommitResult save(Artifact a, String message, User u, boolean force) {
        var result = repo.save(a, message, u, force);
        index.refresh();
        return result;
    }

    public void delete(ArtifactType type, String name, String message, User u) {
        repo.delete(type, name, message, u);
        index.refresh();
    }

    public Optional<Artifact> get(ArtifactType type, String name, String ref) {
        return repo.get(type, name, ref);
    }

    public List<VersionRef> versions(ArtifactType type, String name) {
        return repo.versions(type, name);
    }

    public List<ArtifactMetadata> list(Filter f) { return repo.list(f); }

    public InputStream bundle(ArtifactType type, String name, String ref) {
        return repo.bundle(type, name, ref);
    }
}
```

- [ ] **Step 3: Add mockito dep** — already in `spring-boot-starter-test`.

- [ ] **Step 4: PASS + commit**

```bash
git commit -am "feat(service): ArtifactService orchestrating repo + index refresh"
```

### Task 16: Spring config — wire `LocalGitArtifactRepository` bean

**Files:**
- Create: `src/main/java/com/agentlibrary/config/AppProperties.java`
- Create: `src/main/java/com/agentlibrary/config/StorageConfig.java`

- [ ] **Step 1: `AppProperties.java`**

```java
package com.agentlibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    Path repoPath,
    Path workPath,
    Path usersFile,
    String serverName,
    String serverEmail,
    Path seedPath
) {}
```

- [ ] **Step 2: `StorageConfig.java`**

```java
package com.agentlibrary.config;

import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.metadata.MetadataValidator;
import com.agentlibrary.storage.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class StorageConfig {

    @Bean public MetadataCodec metadataCodec() { return new MetadataCodec(); }
    @Bean public MetadataValidator metadataValidator() { return new MetadataValidator(); }
    @Bean public IndexFile indexFile(MetadataCodec c) { return new IndexFile(c); }
    @Bean(destroyMethod = "shutdown") public WriteQueue writeQueue() { return new WriteQueue(); }

    @Bean
    public ArtifactRepository artifactRepository(AppProperties p, MetadataCodec c,
                                                 MetadataValidator v, IndexFile idx,
                                                 WriteQueue q) {
        return new LocalGitArtifactRepository(
            p.repoPath(), p.workPath(), c, v, idx, q,
            p.serverName() == null ? "agent-library" : p.serverName(),
            p.serverEmail() == null ? "agent-library@localhost" : p.serverEmail()
        );
    }
}
```

- [ ] **Step 3: Update `application.yml`** — add the new fields:

```yaml
app:
  repoPath: ./dev-data/library.git
  workPath: ./dev-data/library-work
  usersFile: ./dev-data/users.yaml
  serverName: agent-library
  serverEmail: agent-library@localhost
  seedPath: classpath:/skills-seed
```

- [ ] **Step 4: Run context-load test; commit**

```bash
./mvnw test -Dtest=AgentLibraryApplicationTests
git commit -am "chore(config): wire ArtifactRepository bean + AppProperties"
```

### Task 17: REST DTOs and `ArtifactApiController` (list + get)

**Files:**
- Create: `src/main/java/com/agentlibrary/web/api/dto/ArtifactDto.java`
- Create: `src/main/java/com/agentlibrary/web/api/ArtifactApiController.java`
- Create: `src/test/java/com/agentlibrary/web/api/ArtifactApiControllerTest.java`

- [ ] **Step 1: Failing MockMvc test for `GET /api/v1/artifacts`**

```java
package com.agentlibrary.web.api;

import com.agentlibrary.domain.*;
import com.agentlibrary.service.ArtifactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArtifactApiController.class)
class ArtifactApiControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ArtifactService service;

    @Test
    @WithMockUser
    void listReturnsJson() throws Exception {
        when(service.list(any())).thenReturn(List.of(sample()));
        mvc.perform(get("/api/v1/artifacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("foo"));
    }

    private ArtifactMetadata sample() {
        return new ArtifactMetadata("foo", "Foo", ArtifactType.SKILL, "1.0.0", "d",
            List.of(), List.of(), null, null, null, Visibility.TEAM,
            Instant.parse("2026-04-19T10:00:00Z"), Instant.parse("2026-04-19T10:00:00Z"),
            new ArtifactMetadata.InstallHints(null, List.of(), null), Map.of());
    }
}
```

- [ ] **Step 2: Implement `ArtifactApiController` with `list` and `get`**

```java
package com.agentlibrary.web.api;

import com.agentlibrary.domain.*;
import com.agentlibrary.service.ArtifactService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/artifacts")
public class ArtifactApiController {

    private final ArtifactService service;

    public ArtifactApiController(ArtifactService service) { this.service = service; }

    @GetMapping
    public List<ArtifactMetadata> list(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String harness,
        @RequestParam(required = false) List<String> tags,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String language,
        @RequestParam(required = false) String author,
        @RequestParam(required = false) String q
    ) {
        Filter f = new Filter(
            type == null ? null : ArtifactType.fromSlug(type),
            harness == null ? null : Harness.fromSlug(harness),
            tags == null ? List.of() : tags,
            category, language, author, q
        );
        return service.list(f);
    }

    @GetMapping("/{type}/{name}")
    public ResponseEntity<Artifact> get(@PathVariable String type,
                                        @PathVariable String name,
                                        @RequestParam(defaultValue = "HEAD") String ref) {
        var a = service.get(ArtifactType.fromSlug(type), name, ref);
        return a.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{type}/{name}/versions")
    public List<VersionRef> versions(@PathVariable String type, @PathVariable String name) {
        return service.versions(ArtifactType.fromSlug(type), name);
    }
}
```

- [ ] **Step 3: Security permit for test** — add a minimal `SecurityConfig` now

Create: `src/main/java/com/agentlibrary/config/SecurityConfig.java`

```java
package com.agentlibrary.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
            .requestMatchers("/healthz", "/login", "/css/**", "/js/**", "/vendor/**").permitAll()
            .anyRequest().authenticated())
            .httpBasic(b -> {})
            .formLogin(f -> f.loginPage("/login").permitAll())
            .csrf(c -> c.ignoringRequestMatchers("/api/**"));
        return http.build();
    }
}
```

- [ ] **Step 4: Run — PASS, commit**

```bash
git commit -am "feat(api): ArtifactApiController list/get/versions + SecurityConfig basics"
```

### Task 18: POST/PUT/DELETE — write endpoints

**Files:**
- Modify: `src/main/java/com/agentlibrary/web/api/ArtifactApiController.java`
- Create: `src/main/java/com/agentlibrary/web/api/dto/SaveArtifactRequest.java`
- Modify: `src/test/java/com/agentlibrary/web/api/ArtifactApiControllerTest.java`

- [ ] **Step 1: DTO**

```java
package com.agentlibrary.web.api.dto;

import com.agentlibrary.domain.ArtifactMetadata;

import java.util.Map;

public record SaveArtifactRequest(
    ArtifactMetadata metadata,
    String primaryContent,
    Map<String, String> base64Files,   // relative path -> base64 bytes
    String message,
    boolean force
) {}
```

- [ ] **Step 2: Failing test**

```java
@Test
@WithMockUser(roles = "USER")
void createPostReturns201() throws Exception {
    when(service.save(any(), any(), any(), anyBoolean()))
        .thenReturn(new com.agentlibrary.domain.CommitResult("sha", "skills/foo@1.0.0", true));
    String body = """
        { "metadata": {"name":"foo","title":"Foo","type":"skill","version":"1.0.0",
                       "harnesses":[],"tags":[],"visibility":"team",
                       "install":{"target":null,"files":[],"merge":null},
                       "typeExtensions":{}},
          "primaryContent":"body","base64Files":{},"message":"init","force":false }
        """;
    mvc.perform(post("/api/v1/artifacts/skill")
        .contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()))
       .andExpect(status().isCreated())
       .andExpect(jsonPath("$.tagName").value("skills/foo@1.0.0"));
}
```

- [ ] **Step 3: Implement endpoints**

Append to `ArtifactApiController`:

```java
@PostMapping("/{type}")
public ResponseEntity<CommitResult> create(
    @PathVariable String type,
    @RequestBody SaveArtifactRequest req,
    java.security.Principal principal
) {
    Artifact a = toArtifact(req, ArtifactType.fromSlug(type));
    User u = new User(principal.getName(), "", java.util.Set.of("USER"));
    CommitResult r = service.save(a, req.message(), u, req.force());
    return ResponseEntity.status(201).body(r);
}

@PutMapping("/{type}/{name}")
public CommitResult update(@PathVariable String type, @PathVariable String name,
                           @RequestBody SaveArtifactRequest req,
                           java.security.Principal principal) {
    if (!name.equals(req.metadata().name()))
        throw new IllegalArgumentException("path name and body name must match");
    Artifact a = toArtifact(req, ArtifactType.fromSlug(type));
    User u = new User(principal.getName(), "", java.util.Set.of("USER"));
    return service.save(a, req.message(), u, req.force());
}

@DeleteMapping("/{type}/{name}")
public ResponseEntity<Void> delete(@PathVariable String type, @PathVariable String name,
                                   @RequestParam(required = false) String message,
                                   java.security.Principal principal) {
    service.delete(ArtifactType.fromSlug(type), name,
                   message, new User(principal.getName(), "", java.util.Set.of("USER")));
    return ResponseEntity.noContent().build();
}

private Artifact toArtifact(SaveArtifactRequest req, ArtifactType type) {
    if (req.metadata().type() != type)
        throw new IllegalArgumentException("metadata.type must match path type");
    java.util.Map<String, byte[]> files = new java.util.HashMap<>();
    if (req.base64Files() != null) {
        for (var e : req.base64Files().entrySet()) {
            files.put(e.getKey(), java.util.Base64.getDecoder().decode(e.getValue()));
        }
    }
    return new Artifact(req.metadata(), req.primaryContent(), files);
}
```

- [ ] **Step 4: Register Jackson module for `Instant`** — Spring Boot's default already handles `Instant` via `jackson-datatype-jsr310` pulled by starter-web.

- [ ] **Step 5: PASS + commit**

```bash
git commit -am "feat(api): POST/PUT/DELETE /artifacts with Principal-based author"
```

### Task 19: `ApiExceptionHandler` + `LookupApiController` + `AdminApiController`

**Files:**
- Create: `src/main/java/com/agentlibrary/web/api/ApiExceptionHandler.java`
- Create: `src/main/java/com/agentlibrary/web/api/LookupApiController.java`
- Create: `src/main/java/com/agentlibrary/web/api/AdminApiController.java`

- [ ] **Step 1: `ApiExceptionHandler.java`**

```java
package com.agentlibrary.web.api;

import com.agentlibrary.metadata.ValidationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.agentlibrary.web.api")
public class ApiExceptionHandler {
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> validation(ValidationException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> illegal(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
```

- [ ] **Step 2: `LookupApiController.java`**

```java
package com.agentlibrary.web.api;

import com.agentlibrary.domain.*;
import com.agentlibrary.index.IndexService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.*;

@RestController
@RequestMapping("/api/v1")
public class LookupApiController {
    private final IndexService idx;
    public LookupApiController(IndexService idx) { this.idx = idx; }

    @GetMapping("/tags")
    public List<String> tags() {
        return idx.snapshot().stream().flatMap(m -> m.tags().stream())
            .distinct().sorted().toList();
    }

    @GetMapping("/types")
    public List<String> types() {
        return Arrays.stream(ArtifactType.values()).map(ArtifactType::slug).toList();
    }

    @GetMapping("/harnesses")
    public List<String> harnesses() {
        return Arrays.stream(Harness.values()).map(Harness::slug).toList();
    }

    @GetMapping("/healthz")
    public Map<String, String> health() { return Map.of("status", "ok"); }
}
```

- [ ] **Step 3: `AdminApiController.java`**

```java
package com.agentlibrary.web.api;

import com.agentlibrary.index.IndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApiController {
    private final IndexService idx;
    public AdminApiController(IndexService idx) { this.idx = idx; }

    @PostMapping("/reindex")
    public ResponseEntity<Void> reindex() {
        idx.refresh();
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Enable method security**

Add to `SecurityConfig`:

```java
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
```

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(api): lookup + admin endpoints + ApiExceptionHandler"
```

---

## Milestone 6 — Auth (file-backed users) + `gen-password-hash.sh`

### Task 20: `UsersFile` loader

**Files:**
- Create: `src/main/java/com/agentlibrary/security/UsersFile.java`
- Create: `src/test/java/com/agentlibrary/security/UsersFileTest.java`

- [ ] **Step 1: Test**

```java
package com.agentlibrary.security;

import com.agentlibrary.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class UsersFileTest {
    @Test
    void parsesUsers(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("users.yaml");
        Files.writeString(f, """
            users:
              - username: robert
                passwordHash: $2a$10$x
                roles: [ADMIN]
              - username: alice
                passwordHash: $2a$10$y
                roles: [USER]
            """);
        UsersFile uf = new UsersFile(f);
        List<User> users = uf.load();
        assertThat(users).hasSize(2);
        assertThat(users.get(0).roles()).contains("ADMIN");
    }

    @Test
    void writesUser(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("users.yaml");
        UsersFile uf = new UsersFile(f);
        uf.save(List.of(new User("u", "$2a$10$h", Set.of("USER"))));
        String txt = Files.readString(f);
        assertThat(txt).contains("username: u");
    }
}
```

- [ ] **Step 2: Implement**

```java
package com.agentlibrary.security;

import com.agentlibrary.domain.User;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class UsersFile {
    private final Path path;
    public UsersFile(Path path) { this.path = path; }

    @SuppressWarnings("unchecked")
    public List<User> load() throws IOException {
        if (!Files.isRegularFile(path)) return List.of();
        Map<String, Object> root = new Yaml().load(Files.readString(path));
        if (root == null) return List.of();
        List<Map<String, Object>> entries = (List<Map<String, Object>>) root.getOrDefault("users", List.of());
        List<User> out = new ArrayList<>();
        for (var e : entries) {
            out.add(new User(
                (String) e.get("username"),
                (String) e.get("passwordHash"),
                new LinkedHashSet<>((List<String>) e.getOrDefault("roles", List.of("USER")))
            ));
        }
        return out;
    }

    public synchronized void save(List<User> users) throws IOException {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.username());
            m.put("passwordHash", u.passwordHash());
            m.put("roles", new ArrayList<>(u.roles()));
            entries.add(m);
        }
        Map<String, Object> root = Map.of("users", entries);
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Files.createDirectories(path.getParent());
        Files.writeString(path, new Yaml(opts).dump(root));
    }
}
```

- [ ] **Step 3: PASS + commit**

```bash
git commit -am "feat(security): UsersFile YAML loader/saver"
```

### Task 21: `FileUserDetailsService` + Spring wiring

**Files:**
- Create: `src/main/java/com/agentlibrary/security/FileUserDetailsService.java`
- Modify: `src/main/java/com/agentlibrary/config/SecurityConfig.java`

- [ ] **Step 1: Implement**

```java
package com.agentlibrary.security;

import com.agentlibrary.config.AppProperties;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FileUserDetailsService implements UserDetailsService {

    private final UsersFile usersFile;
    private final AtomicReference<java.util.List<com.agentlibrary.domain.User>> cache =
        new AtomicReference<>(java.util.List.of());

    public FileUserDetailsService(AppProperties p) throws IOException {
        this.usersFile = new UsersFile(p.usersFile());
        reload();
    }

    public synchronized void reload() throws IOException {
        cache.set(usersFile.load());
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return cache.get().stream()
            .filter(u -> u.username().equals(username))
            .findFirst()
            .map(u -> User.withUsername(u.username())
                .password(u.passwordHash())
                .roles(u.roles().toArray(String[]::new))
                .build())
            .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
```

- [ ] **Step 2: Add `PasswordEncoder` bean to `SecurityConfig`**

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10);
}
```

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(security): FileUserDetailsService + BCryptPasswordEncoder"
```

### Task 22: `LoginRateLimiter` (bucket4j)

**Files:**
- Create: `src/main/java/com/agentlibrary/security/LoginRateLimiter.java`
- Modify: `src/main/java/com/agentlibrary/config/SecurityConfig.java`

- [ ] **Step 1: Implement**

```java
package com.agentlibrary.security;

import io.github.bucket4j.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LoginRateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String ip) {
        return buckets.computeIfAbsent(ip, k -> Bucket.builder()
            .addLimit(limit -> limit.capacity(10).refillIntervally(10, Duration.ofMinutes(5)))
            .build()
        ).tryConsume(1);
    }
}
```

- [ ] **Step 2: Add a `Filter` that rejects excess login attempts**

Create `src/main/java/com/agentlibrary/security/LoginRateLimitFilter.java`:

```java
package com.agentlibrary.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginRateLimitFilter implements Filter {
    private final LoginRateLimiter limiter;
    public LoginRateLimitFilter(LoginRateLimiter limiter) { this.limiter = limiter; }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest r = (HttpServletRequest) req;
        if ("POST".equals(r.getMethod()) && "/login".equals(r.getRequestURI())
                && !limiter.tryConsume(r.getRemoteAddr())) {
            ((HttpServletResponse) resp).sendError(429, "Too many login attempts");
            return;
        }
        chain.doFilter(req, resp);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(security): bucket4j-based login rate limit"
```

### Task 23: `BcryptCli` + `scripts/gen-password-hash.sh`

**Files:**
- Create: `src/main/java/com/agentlibrary/security/BcryptCli.java`
- Create: `scripts/gen-password-hash.sh`

- [ ] **Step 1: Implement**

```java
package com.agentlibrary.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptCli {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: gen-password-hash <password>");
            System.exit(2);
        }
        System.out.println(new BCryptPasswordEncoder(10).encode(args[0]));
    }
}
```

- [ ] **Step 2: `scripts/gen-password-hash.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail
if [ $# -ne 1 ]; then
    echo "Usage: $0 <password>" >&2
    exit 2
fi
exec java -cp /app/app.jar \
    -Dloader.main=com.agentlibrary.security.BcryptCli \
    org.springframework.boot.loader.launch.PropertiesLauncher "$1"
```

Make executable:

```bash
chmod +x scripts/gen-password-hash.sh
```

- [ ] **Step 3: Commit**

```bash
git add scripts src/main/java/com/agentlibrary/security/BcryptCli.java
git commit -m "feat(security): BcryptCli + gen-password-hash.sh helper"
```

---

## Milestone 7 — Web UI (Thymeleaf + HTMX)

### Task 24: Base layout + static assets

**Files:**
- Create: `src/main/resources/templates/layout.html`
- Create: `src/main/resources/templates/fragments/header.html`
- Create: `src/main/resources/templates/login.html`
- Create: `src/main/resources/static/css/app.css`
- Create: `src/main/resources/static/js/htmx.min.js` (download at build)

- [ ] **Step 1: `layout.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
    <meta charset="UTF-8">
    <title th:text="${pageTitle ?: 'Agent Library'}">Agent Library</title>
    <link rel="stylesheet" href="/css/app.css">
    <script src="/js/htmx.min.js" defer></script>
</head>
<body>
    <header th:replace="~{fragments/header :: header}"></header>
    <main>
        <div th:insert="~{::content}"></div>
    </main>
</body>
</html>
```

(If using layout dialect, add the dependency; otherwise use `th:fragment` pattern below — for simplicity use plain `th:insert`/`th:replace` without layout dialect.)

Re-do without layout dialect — cleaner:

`layout.html`:

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="${pageTitle ?: 'Agent Library'}">Agent Library</title>
    <link rel="stylesheet" href="/css/app.css">
    <script src="/js/htmx.min.js" defer></script>
</head>
<body>
    <header th:replace="~{fragments/header :: header}"></header>
    <main th:replace="${contentFragment}"></main>
</body>
</html>
```

Each page will set `contentFragment` via the controller model.

Actually simpler: use Thymeleaf layout dialect. Add dep.

In `pom.xml`:

```xml
<dependency>
    <groupId>nz.net.ultraq.thymeleaf</groupId>
    <artifactId>thymeleaf-layout-dialect</artifactId>
    <version>3.3.0</version>
</dependency>
```

And keep original `layout.html` with `layout:*` namespace. Each page:

```html
<html layout:decorate="~{layout}">
    <body>
        <div layout:fragment="content">...</div>
    </body>
</html>
```

- [ ] **Step 2: `fragments/header.html`**

```html
<header th:fragment="header">
    <nav>
        <a href="/">Library</a>
        <a href="/browse">Browse</a>
        <a href="/new">New</a>
        <a href="/admin" sec:authorize="hasRole('ADMIN')">Admin</a>
        <form th:action="@{/logout}" method="post" style="display:inline">
            <button type="submit">Logout</button>
        </form>
    </nav>
</header>
```

Add Spring Security dialect:

```xml
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```

And xmlns in header: `xmlns:sec="http://www.thymeleaf.org/extras/spring-security"`.

- [ ] **Step 3: `login.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body>
    <h1>Sign in</h1>
    <form th:action="@{/login}" method="post">
        <label>Username <input name="username"></label>
        <label>Password <input type="password" name="password"></label>
        <button type="submit">Log in</button>
    </form>
    <p th:if="${param.error}" class="error">Invalid credentials</p>
</body>
</html>
```

- [ ] **Step 4: `app.css` — minimal styling**

```css
:root { color-scheme: light dark; font-family: system-ui, sans-serif; }
body { margin: 0; padding: 0 2rem 2rem; max-width: 72rem; margin-inline: auto; }
nav { display: flex; gap: 1rem; padding: 1rem 0; }
.card { border: 1px solid #ccc; padding: 1rem; border-radius: 4px; margin-bottom: 1rem; }
.error { color: crimson; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 1rem; }
```

- [ ] **Step 5: Download HTMX**

Run:
```bash
curl -Lo src/main/resources/static/js/htmx.min.js https://unpkg.com/htmx.org@2.0.3/dist/htmx.min.js
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates src/main/resources/static pom.xml
git commit -m "feat(ui): base layout, header, login page, HTMX asset"
```

### Task 25: `DashboardController` + `dashboard.html`

**Files:**
- Create: `src/main/java/com/agentlibrary/web/ui/DashboardController.java`
- Create: `src/main/resources/templates/dashboard.html`

- [ ] **Step 1: Controller**

```java
package com.agentlibrary.web.ui;

import com.agentlibrary.domain.ArtifactType;
import com.agentlibrary.index.IndexService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

@Controller
public class DashboardController {
    private final IndexService idx;
    public DashboardController(IndexService idx) { this.idx = idx; }

    @GetMapping("/")
    public String dashboard(Model model) {
        var snapshot = idx.snapshot();
        var counts = Arrays.stream(ArtifactType.values())
            .collect(java.util.stream.Collectors.toMap(
                ArtifactType::slug,
                t -> snapshot.stream().filter(m -> m.type() == t).count()));
        model.addAttribute("counts", counts);
        model.addAttribute("total", snapshot.size());
        return "dashboard";
    }
}
```

- [ ] **Step 2: `dashboard.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org"
      layout:decorate="~{layout}"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<body>
    <div layout:fragment="content">
        <h1>Dashboard</h1>
        <p>Total artifacts: <span th:text="${total}">0</span></p>
        <ul>
            <li th:each="e : ${counts}">
                <a th:href="@{/browse(type=${e.key})}" th:text="${e.key} + ': ' + ${e.value}"></a>
            </li>
        </ul>
    </div>
</body>
</html>
```

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(ui): DashboardController + view"
```

### Task 26: `BrowseController` with HTMX filter swapping

**Files:**
- Create: `src/main/java/com/agentlibrary/web/ui/BrowseController.java`
- Create: `src/main/resources/templates/browse.html`
- Create: `src/main/resources/templates/fragments/artifact-card.html`
- Create: `src/main/resources/templates/fragments/results.html`

- [ ] **Step 1: Controller**

```java
package com.agentlibrary.web.ui;

import com.agentlibrary.domain.*;
import com.agentlibrary.service.ArtifactService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/browse")
public class BrowseController {
    private final ArtifactService service;
    public BrowseController(ArtifactService service) { this.service = service; }

    @GetMapping
    public String browse(Filter filter, Model model,
                         @RequestHeader(name = "HX-Request", required = false) String hx) {
        List<ArtifactMetadata> items = service.list(filter);
        model.addAttribute("items", items);
        return hx != null ? "fragments/results :: results" : "browse";
    }
}
```

Spring can bind `Filter` via constructor params only if fields map to request params; `Filter` is a record whose param names are the query keys — that works with Spring's `@ModelAttribute` when passed as a method argument **with** a no-args factory. Records don't have no-args constructors, so use explicit `@RequestParam`s:

Replace with:

```java
@GetMapping
public String browse(
    @RequestParam(required = false) String type,
    @RequestParam(required = false) String harness,
    @RequestParam(required = false) List<String> tags,
    @RequestParam(required = false) String category,
    @RequestParam(required = false) String language,
    @RequestParam(required = false) String author,
    @RequestParam(required = false) String q,
    Model model,
    @RequestHeader(name = "HX-Request", required = false) String hx
) {
    Filter filter = new Filter(
        type == null ? null : ArtifactType.fromSlug(type),
        harness == null ? null : Harness.fromSlug(harness),
        tags == null ? List.of() : tags,
        category, language, author, q);
    model.addAttribute("items", service.list(filter));
    model.addAttribute("filter", filter);
    return hx != null ? "fragments/results :: results" : "browse";
}
```

- [ ] **Step 2: `fragments/artifact-card.html`**

```html
<article th:fragment="card(m)" class="card">
    <h3><a th:href="@{'/a/' + ${m.type().slug()} + '/' + ${m.name()}}" th:text="${m.title()}">Title</a></h3>
    <p th:text="${m.description()}">desc</p>
    <small>
        <span th:text="${m.type().slug()}">type</span> |
        <span th:text="${m.version()}">ver</span> |
        <span th:each="h : ${m.harnesses()}" th:text="${h.slug()}"></span>
    </small>
</article>
```

- [ ] **Step 3: `fragments/results.html`**

```html
<div th:fragment="results" id="results" class="grid">
    <article th:each="m : ${items}" th:replace="~{fragments/artifact-card :: card(${m})}"></article>
    <p th:if="${#lists.isEmpty(items)}">No artifacts.</p>
</div>
```

- [ ] **Step 4: `browse.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org"
      layout:decorate="~{layout}"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<body>
    <div layout:fragment="content">
        <h1>Browse</h1>
        <form id="filters" hx-get="/browse" hx-target="#results" hx-trigger="input changed delay:250ms, change">
            <input name="q" placeholder="search" th:value="${filter.query()}">
            <select name="type">
                <option value="">any type</option>
                <option th:each="t : ${T(com.agentlibrary.domain.ArtifactType).values()}"
                        th:value="${t.slug()}" th:text="${t.slug()}"
                        th:selected="${filter.type() != null and filter.type() == t}"></option>
            </select>
            <select name="harness">
                <option value="">any harness</option>
                <option th:each="h : ${T(com.agentlibrary.domain.Harness).values()}"
                        th:value="${h.slug()}" th:text="${h.slug()}"
                        th:selected="${filter.harness() != null and filter.harness() == h}"></option>
            </select>
        </form>
        <div th:replace="~{fragments/results :: results}"></div>
    </div>
</body>
</html>
```

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(ui): browse page with HTMX-driven filters"
```

### Task 27: Artifact detail view + version dropdown

**Files:**
- Create: `src/main/java/com/agentlibrary/web/ui/ArtifactViewController.java`
- Create: `src/main/resources/templates/artifact.html`

- [ ] **Step 1: Controller**

```java
package com.agentlibrary.web.ui;

import com.agentlibrary.domain.ArtifactType;
import com.agentlibrary.service.ArtifactService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/a")
public class ArtifactViewController {
    private final ArtifactService service;
    public ArtifactViewController(ArtifactService s) { this.service = s; }

    @GetMapping("/{type}/{name}")
    public String view(@PathVariable String type, @PathVariable String name,
                       @RequestParam(defaultValue = "HEAD") String ref,
                       Model model) {
        var type0 = ArtifactType.fromSlug(type);
        var a = service.get(type0, name, ref).orElseThrow();
        model.addAttribute("artifact", a);
        model.addAttribute("versions", service.versions(type0, name));
        model.addAttribute("ref", ref);
        return "artifact";
    }
}
```

- [ ] **Step 2: `artifact.html`**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org"
      layout:decorate="~{layout}"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<body>
    <div layout:fragment="content">
        <h1 th:text="${artifact.metadata().title()}">Title</h1>
        <p th:text="${artifact.metadata().description()}">desc</p>

        <form hx-get th:hx-get="@{'/a/' + ${artifact.metadata().type().slug()} + '/' + ${artifact.metadata().name()}}"
              hx-target="body" hx-push-url="true">
            <label>Version:
                <select name="ref" onchange="this.form.requestSubmit()">
                    <option value="HEAD" th:selected="${ref == 'HEAD'}">HEAD (latest)</option>
                    <option th:each="v : ${versions}"
                            th:value="${v.version()}" th:text="${v.version()}"
                            th:selected="${ref == v.version()}"></option>
                </select>
            </label>
        </form>

        <h2>Install</h2>
        <pre><code th:text="'agent-lib install ' + ${artifact.metadata().type().slug()} + '/' + ${artifact.metadata().name()} + '@' + ${artifact.metadata().version()}"></code></pre>

        <h2>Content</h2>
        <pre th:text="${artifact.primaryContent()}"></pre>

        <a th:href="@{'/edit/' + ${artifact.metadata().type().slug()} + '/' + ${artifact.metadata().name()}}">Edit</a>
    </div>
</body>
</html>
```

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(ui): artifact detail view with version switcher"
```

### Task 28: New / Edit form + delete

**Files:**
- Create: `src/main/java/com/agentlibrary/web/ui/EditController.java`
- Create: `src/main/resources/templates/new.html`
- Create: `src/main/resources/templates/edit.html`

- [ ] **Step 1: Controller**

```java
package com.agentlibrary.web.ui;

import com.agentlibrary.domain.*;
import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.service.ArtifactService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

@Controller
public class EditController {
    private final ArtifactService service;
    private final MetadataCodec codec;
    public EditController(ArtifactService s, MetadataCodec c) { this.service = s; this.codec = c; }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) String type, Model model) {
        model.addAttribute("types", ArtifactType.values());
        model.addAttribute("harnesses", Harness.values());
        model.addAttribute("selectedType", type == null ? "skill" : type);
        return "new";
    }

    @PostMapping("/new")
    public String create(@RequestParam String type,
                         @RequestParam String name,
                         @RequestParam String title,
                         @RequestParam String version,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) List<String> harnesses,
                         @RequestParam(required = false) String tags,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String language,
                         @RequestParam(required = false) String installTarget,
                         @RequestParam String content,
                         Principal principal) {
        ArtifactType t = ArtifactType.fromSlug(type);
        ArtifactMetadata meta = new ArtifactMetadata(
            name, title, t, version, description,
            harnesses == null ? List.of() : harnesses.stream().map(Harness::fromSlug).toList(),
            tags == null ? List.of() : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
            category, language, principal.getName(), Visibility.TEAM,
            Instant.now(), Instant.now(),
            new ArtifactMetadata.InstallHints(installTarget, List.of(t.primaryFile()), null),
            Map.of());
        service.save(new Artifact(meta, content, Map.of()), "create " + name,
                     new User(principal.getName(), "", Set.of("USER")), false);
        return "redirect:/a/" + type + "/" + name;
    }

    @GetMapping("/edit/{type}/{name}")
    public String editForm(@PathVariable String type, @PathVariable String name, Model model) {
        var a = service.get(ArtifactType.fromSlug(type), name, "HEAD").orElseThrow();
        model.addAttribute("artifact", a);
        model.addAttribute("harnesses", Harness.values());
        return "edit";
    }

    @PostMapping("/edit/{type}/{name}")
    public String update(@PathVariable String type, @PathVariable String name,
                         @RequestParam String title, @RequestParam String version,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) List<String> harnesses,
                         @RequestParam(required = false) String tags,
                         @RequestParam String content,
                         Principal principal) {
        ArtifactType t = ArtifactType.fromSlug(type);
        var existing = service.get(t, name, "HEAD").orElseThrow();
        var old = existing.metadata();
        ArtifactMetadata meta = new ArtifactMetadata(
            name, title, t, version, description,
            harnesses == null ? List.of() : harnesses.stream().map(Harness::fromSlug).toList(),
            tags == null ? List.of() : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
            old.category(), old.language(), principal.getName(), old.visibility(),
            old.created(), Instant.now(),
            old.install(), old.typeExtensions());
        service.save(new Artifact(meta, content, existing.extraFiles()),
                     "update " + name,
                     new User(principal.getName(), "", Set.of("USER")), false);
        return "redirect:/a/" + type + "/" + name;
    }

    @PostMapping("/delete/{type}/{name}")
    public String delete(@PathVariable String type, @PathVariable String name, Principal principal) {
        service.delete(ArtifactType.fromSlug(type), name, "delete " + name,
                       new User(principal.getName(), "", Set.of("USER")));
        return "redirect:/browse";
    }
}
```

- [ ] **Step 2: `new.html` and `edit.html`** — plain forms pointing at the endpoints above. Both include a `<textarea name="content">` and standard inputs. Omitted here for brevity because the fields map 1-1 with the `@RequestParam` list in the controller. Include a Monaco container:

```html
<textarea name="content" id="content-editor" rows="30" cols="80"
          th:text="${artifact?.primaryContent() ?: ''}"></textarea>
```

For Monaco integration, add `<script src="/vendor/monaco/loader.js"></script>` and attach to the textarea in later polish — plan notes this is a follow-on. For first release just use `<textarea>`.

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(ui): new/edit/delete with textarea editor"
```

### Task 29: Admin page — reindex + users list (read-only first)

**Files:**
- Create: `src/main/java/com/agentlibrary/web/ui/AdminController.java`
- Create: `src/main/resources/templates/admin.html`

- [ ] **Step 1: Controller**

```java
package com.agentlibrary.web.ui;

import com.agentlibrary.index.IndexService;
import com.agentlibrary.security.FileUserDetailsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final IndexService idx;
    private final FileUserDetailsService users;

    public AdminController(IndexService idx, FileUserDetailsService users) {
        this.idx = idx; this.users = users;
    }

    @GetMapping
    public String view(Model model) {
        model.addAttribute("total", idx.snapshot().size());
        return "admin";
    }

    @PostMapping("/reindex")
    public String reindex() {
        idx.refresh();
        return "redirect:/admin";
    }
}
```

- [ ] **Step 2: `admin.html`** — has a reindex POST button and a placeholder for user mgmt.

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(ui): admin page with reindex button"
```

---

## Milestone 8 — Install Manifest + Bundle API

### Task 30: `InstallManifest` + `InstallManifestResolver`

**Files:**
- Create: `src/main/java/com/agentlibrary/install/InstallManifest.java`
- Create: `src/main/java/com/agentlibrary/install/InstallManifestResolver.java`
- Create: `src/test/java/com/agentlibrary/install/InstallManifestResolverTest.java`

- [ ] **Step 1: `InstallManifest.java`**

```java
package com.agentlibrary.install;

import java.util.List;

public record InstallManifest(
    String targetPath,
    List<String> files,
    String mergeStrategy           // null | "json-merge-at=<jsonPath>" | "append"
) {}
```

- [ ] **Step 2: Tests**

```java
package com.agentlibrary.install;

import com.agentlibrary.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class InstallManifestResolverTest {
    InstallManifestResolver r = new InstallManifestResolver();

    ArtifactMetadata meta(ArtifactType t, String name, String target, String merge,
                          Map<String,Object> ext) {
        return new ArtifactMetadata(name, name, t, "1.0.0", "d",
            List.of(Harness.CLAUDE), List.of(), null, null, null, Visibility.TEAM,
            Instant.now(), Instant.now(),
            new ArtifactMetadata.InstallHints(target, List.of(t.primaryFile()), merge),
            ext);
    }

    @Test
    void skill_forClaude_usesClaudeSkillsPath() {
        var m = meta(ArtifactType.SKILL, "foo", null, null, Map.of());
        var im = r.resolve(m, Harness.CLAUDE);
        assertThat(im.targetPath()).isEqualTo("~/.claude/skills/foo/");
        assertThat(im.files()).containsExactly("skill.md");
    }

    @Test
    void claudeAgent_goesIntoClaudeAgentsFile() {
        var m = meta(ArtifactType.AGENT_CLAUDE, "reviewer", null, null, Map.of());
        assertThat(r.resolve(m, Harness.CLAUDE).targetPath()).isEqualTo(".claude/agents/reviewer.md");
    }

    @Test
    void copilotAgent_goesIntoGithubAgentsFile() {
        var m = meta(ArtifactType.AGENT_COPILOT, "linter", null, null, Map.of());
        assertThat(r.resolve(m, Harness.COPILOT).targetPath()).isEqualTo(".github/agents/linter.md");
    }

    @Test
    void mcp_usesJsonMergeStrategy() {
        var m = meta(ArtifactType.MCP, "fs", null, null, Map.of("serverName", "fs"));
        var im = r.resolve(m, Harness.CLAUDE);
        assertThat(im.targetPath()).isEqualTo("~/.claude/settings.json");
        assertThat(im.mergeStrategy()).isEqualTo("json-merge-at=mcpServers.fs");
    }

    @Test
    void explicitTargetOverride_wins() {
        var m = meta(ArtifactType.SKILL, "foo", "/custom/path/{name}/", null, Map.of());
        assertThat(r.resolve(m, Harness.CLAUDE).targetPath()).isEqualTo("/custom/path/foo/");
    }

    @Test
    void claudeMd_appendsToNearestFile() {
        var m = meta(ArtifactType.CLAUDE_MD, "guard", null, null, Map.of());
        var im = r.resolve(m, Harness.CLAUDE);
        assertThat(im.targetPath()).isEqualTo("CLAUDE.md");
        assertThat(im.mergeStrategy()).isEqualTo("append");
    }
}
```

- [ ] **Step 3: Implement**

```java
package com.agentlibrary.install;

import com.agentlibrary.domain.*;
import org.springframework.stereotype.Service;

@Service
public class InstallManifestResolver {

    public InstallManifest resolve(ArtifactMetadata m, Harness harness) {
        String explicit = m.install() == null ? null : m.install().target();
        String merge = m.install() == null ? null : m.install().merge();

        String target = explicit != null ? explicit : defaultTarget(m, harness);
        target = target.replace("{name}", m.name());

        if (merge == null) merge = defaultMerge(m);

        var files = m.install() != null && m.install().files() != null && !m.install().files().isEmpty()
            ? m.install().files()
            : java.util.List.of(m.type().primaryFile());

        return new InstallManifest(target, files, merge);
    }

    private String defaultTarget(ArtifactMetadata m, Harness harness) {
        return switch (m.type()) {
            case SKILL         -> "~/.claude/skills/" + m.name() + "/";
            case AGENT_CLAUDE  -> ".claude/agents/" + m.name() + ".md";
            case AGENT_COPILOT -> ".github/agents/" + m.name() + ".md";
            case MCP           -> harness == Harness.CLAUDE
                                    ? "~/.claude/settings.json"
                                    : "./.mcp.json";
            case COMMAND       -> "~/.claude/commands/" + m.name() + ".md";
            case HOOK          -> "~/.claude/settings.json";
            case CLAUDE_MD     -> "CLAUDE.md";
            case OPENCODE      -> "~/.config/opencode/" + m.name() + "/config.yaml";
            case PROMPT        -> "~/.claude/prompts/" + m.name() + ".md";
        };
    }

    private String defaultMerge(ArtifactMetadata m) {
        return switch (m.type()) {
            case MCP -> "json-merge-at=mcpServers." +
                m.typeExtensions().getOrDefault("serverName", m.name());
            case HOOK -> "json-merge-at=hooks." +
                m.typeExtensions().getOrDefault("event", "any") + "[]";
            case CLAUDE_MD -> "append";
            default -> null;
        };
    }
}
```

- [ ] **Step 4: PASS + commit**

```bash
git commit -am "feat(install): InstallManifestResolver per-type + per-harness defaults"
```

### Task 31: `InstallApiController` — manifest + bundle endpoints

**Files:**
- Create: `src/main/java/com/agentlibrary/web/api/InstallApiController.java`

- [ ] **Step 1: Implement**

```java
package com.agentlibrary.web.api;

import com.agentlibrary.domain.*;
import com.agentlibrary.install.*;
import com.agentlibrary.service.ArtifactService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/artifacts/{type}/{name}")
public class InstallApiController {

    private final ArtifactService service;
    private final InstallManifestResolver resolver;

    public InstallApiController(ArtifactService s, InstallManifestResolver r) {
        this.service = s; this.resolver = r;
    }

    @GetMapping("/install-manifest")
    public InstallManifest manifest(@PathVariable String type, @PathVariable String name,
                                    @RequestParam(defaultValue = "claude") String harness) {
        var a = service.get(ArtifactType.fromSlug(type), name, "HEAD").orElseThrow();
        return resolver.resolve(a.metadata(), Harness.fromSlug(harness));
    }

    @GetMapping("/bundle")
    public ResponseEntity<InputStreamResource> bundle(@PathVariable String type,
                                                       @PathVariable String name,
                                                       @RequestParam(defaultValue = "HEAD") String ref) {
        var is = service.bundle(ArtifactType.fromSlug(type), name, ref);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/gzip"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + name + ".tar.gz\"")
            .body(new InputStreamResource(is));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git commit -am "feat(install): /install-manifest and /bundle endpoints"
```

---

## Milestone 9 — Install Skill + CLI (seed content)

### Task 32: `agent-lib.sh` — core subcommands

**Files:**
- Create: `skills-seed/agent-library-install/skill.md`
- Create: `skills-seed/agent-library-install/scripts/agent-lib.sh`
- Create: `skills-seed/agent-library-install/scripts/agent-lib-common.sh`
- Create: `skills-seed/agent-library-install/README.md`

- [ ] **Step 1: `scripts/agent-lib-common.sh`**

```bash
#!/usr/bin/env bash
# shellcheck disable=SC2155
set -euo pipefail

CONFIG_DIR="${AGENT_LIB_CONFIG:-$HOME/.config/agent-lib}"
CREDS_FILE="$CONFIG_DIR/creds"
CONFIG_FILE="$CONFIG_DIR/config"

load_config() {
    [ -f "$CONFIG_FILE" ] && . "$CONFIG_FILE"
    SERVER="${AGENT_LIB_SERVER:-${SERVER:-http://localhost:8080}}"
}

load_creds() {
    if [ -f "$CREDS_FILE" ]; then
        . "$CREDS_FILE"
    fi
    if [ -z "${USER:-}" ] || [ -z "${PASS:-}" ]; then
        echo "Not logged in. Run: agent-lib login" >&2
        exit 1
    fi
}

api() {
    local method="$1" path="$2"
    shift 2
    curl -sS -u "$USER:$PASS" -X "$method" "$SERVER$path" "$@"
}

require() {
    for cmd in "$@"; do
        command -v "$cmd" >/dev/null 2>&1 || { echo "Missing: $cmd" >&2; exit 1; }
    done
}
```

- [ ] **Step 2: `scripts/agent-lib.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/agent-lib-common.sh"

require curl jq tar

cmd_login() {
    mkdir -p "$CONFIG_DIR"
    chmod 700 "$CONFIG_DIR"
    read -r -p "Username: " u
    read -r -s -p "Password: " p; echo
    umask 077
    cat > "$CREDS_FILE" <<EOF
USER="$u"
PASS="$p"
EOF
}

cmd_config() {
    case "${1:-}" in
        set)
            shift
            [ "${1:-}" = "server" ] || { echo "usage: agent-lib config set server <url>"; exit 2; }
            shift
            mkdir -p "$CONFIG_DIR"
            printf 'AGENT_LIB_SERVER="%s"\n' "$1" > "$CONFIG_FILE"
            ;;
        *) echo "usage: agent-lib config set server <url>"; exit 2;;
    esac
}

cmd_search() {
    local q="" type="" harness="" tag=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --type) type="$2"; shift 2;;
            --harness) harness="$2"; shift 2;;
            --tag) tag="$2"; shift 2;;
            *) q="$1"; shift;;
        esac
    done
    load_creds
    local q_esc
    q_esc=$(printf '%s' "$q" | jq -rR @uri)
    local url="/api/v1/artifacts?q=$q_esc"
    [ -n "$type" ] && url="$url&type=$type"
    [ -n "$harness" ] && url="$url&harness=$harness"
    [ -n "$tag" ] && url="$url&tags=$tag"
    api GET "$url" | jq -r '.[] | "\(.type)/\(.name)@\(.version)  \(.title)"'
}

cmd_show() {
    local ref="$1"
    local type="${ref%%/*}"
    local rest="${ref#*/}"
    local name version
    if [[ "$rest" == *"@"* ]]; then
        name="${rest%%@*}"
        version="${rest#*@}"
    else
        name="$rest"
        version="HEAD"
    fi
    load_creds
    api GET "/api/v1/artifacts/$type/$name?ref=$version" | jq
}

cmd_list_versions() {
    local ref="$1"
    local type="${ref%%/*}"
    local name="${ref#*/}"
    load_creds
    api GET "/api/v1/artifacts/$type/$name/versions" | jq -r '.[] | "\(.version)  \(.commitSha)"'
}

cmd_install() {
    local ref="$1"; shift
    local harness="claude" fetch_only="" dest="" dry=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --harness) harness="$2"; shift 2;;
            --fetch-only) fetch_only=1; shift;;
            --dest) dest="$2"; shift 2;;
            --dry-run) dry=1; shift;;
            *) echo "unknown flag: $1" >&2; exit 2;;
        esac
    done
    local type="${ref%%/*}" rest="${ref#*/}" name version
    if [[ "$rest" == *"@"* ]]; then name="${rest%%@*}"; version="${rest#*@}";
    else name="$rest"; version="HEAD"; fi

    load_creds
    local manifest
    manifest=$(api GET "/api/v1/artifacts/$type/$name/install-manifest?harness=$harness")
    local target merge
    target=$(printf '%s' "$manifest" | jq -r .targetPath)
    merge=$(printf '%s' "$manifest" | jq -r .mergeStrategy)

    local tmp
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT

    api GET "/api/v1/artifacts/$type/$name/bundle?ref=$version" -o "$tmp/bundle.tar.gz"
    tar -xzf "$tmp/bundle.tar.gz" -C "$tmp"
    rm "$tmp/bundle.tar.gz"

    if [ -n "$fetch_only" ]; then
        local out="${dest:-./artifact/$name}"
        mkdir -p "$out"
        cp -R "$tmp"/. "$out"/
        echo "Fetched to $out"
        return
    fi

    local resolved="$target"
    case "$resolved" in
        "~"*) resolved="$HOME${resolved#\~}";;
    esac

    if [ "$merge" = "null" ] || [ -z "$merge" ]; then
        if [ -n "$dry" ]; then echo "[dry] copy $tmp -> $resolved"; return; fi
        if [[ "$resolved" == */ ]]; then
            mkdir -p "$resolved"
            cp -R "$tmp"/. "$resolved"
        else
            mkdir -p "$(dirname "$resolved")"
            cp "$tmp/$(ls "$tmp" | head -n1)" "$resolved"
        fi
    elif [[ "$merge" == json-merge-at=* ]]; then
        local jsonPath="${merge#json-merge-at=}"
        local srcJson
        srcJson=$(cat "$tmp"/*.json)
        if [ -n "$dry" ]; then echo "[dry] json-merge $srcJson into $resolved at $jsonPath"; return; fi
        mkdir -p "$(dirname "$resolved")"
        [ -f "$resolved" ] || echo '{}' > "$resolved"
        tmp_out="$(mktemp)"
        jq --arg p "$jsonPath" --argjson v "$srcJson" '
            def setpath_dot($pstr; $val):
                ($pstr | split(".")) as $p | setpath($p; $val);
            setpath_dot($p; $v)
        ' "$resolved" > "$tmp_out"
        mv "$tmp_out" "$resolved"
    elif [ "$merge" = "append" ]; then
        if [ -n "$dry" ]; then echo "[dry] append $tmp contents to $resolved"; return; fi
        local first
        first=$(ls "$tmp" | head -n1)
        cat "$tmp/$first" >> "$resolved"
    else
        echo "Unknown merge strategy: $merge" >&2; exit 1
    fi
    echo "Installed $type/$name@$version -> $resolved"
}

usage() {
    cat >&2 <<EOF
Usage: agent-lib <command> [args]

Commands:
  search <query> [--type T] [--harness H] [--tag T]
  show <type>/<name>[@ver]
  list-versions <type>/<name>
  install <type>/<name>[@ver] [--harness H] [--fetch-only] [--dest DIR] [--dry-run]
  login
  config set server <url>
EOF
    exit 2
}

main() {
    load_config
    local cmd="${1:-}"; shift || true
    case "$cmd" in
        search) cmd_search "$@";;
        show) cmd_show "$@";;
        install) cmd_install "$@";;
        list-versions) cmd_list_versions "$@";;
        login) cmd_login "$@";;
        config) cmd_config "$@";;
        *) usage;;
    esac
}

main "$@"
```

- [ ] **Step 3: `skill.md`**

```markdown
---
name: agent-library-install
title: Agent Library Install
type: skill
version: 1.0.0
description: Search and install agent artifacts from the library.
harnesses: [claude]
tags: [install, library]
category: tooling
language: en
author: system
visibility: team
install:
  target: ~/.claude/skills/{name}/
  files: [skill.md, scripts/agent-lib.sh, scripts/agent-lib-common.sh]
  merge: null
---

# Agent Library Install

Use `scripts/agent-lib.sh` to search and install artifacts from the library.

**To search:**
Run: `./scripts/agent-lib.sh search "<query>"`

**To install a skill into Claude:**
Run: `./scripts/agent-lib.sh install skill/<name> --harness claude`

Before any merge-style install (MCP, hook, claude-md), confirm with the user; these modify existing files. Use `--dry-run` first.
```

- [ ] **Step 4: `README.md`** — short note for manual CLI use.

- [ ] **Step 5: `chmod +x`**

```bash
chmod +x skills-seed/agent-library-install/scripts/*.sh
```

- [ ] **Step 6: Commit**

```bash
git add skills-seed
git commit -m "feat(seed): agent-library-install skill + CLI"
```

### Task 33: `Bootstrapper` — first-run seed

**Files:**
- Create: `src/main/java/com/agentlibrary/storage/Bootstrapper.java`
- Modify: `src/main/java/com/agentlibrary/AgentLibraryApplication.java` (run on startup)

- [ ] **Step 1: Implement**

```java
package com.agentlibrary.storage;

import com.agentlibrary.config.AppProperties;
import com.agentlibrary.domain.*;
import com.agentlibrary.metadata.MetadataCodec;
import com.agentlibrary.service.ArtifactService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

@Component
public class Bootstrapper implements CommandLineRunner {

    private final AppProperties props;
    private final ArtifactService service;
    private final MetadataCodec codec;
    private final com.agentlibrary.security.UsersFile usersFile;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;

    public Bootstrapper(AppProperties p, ArtifactService s, MetadataCodec c,
                        org.springframework.security.crypto.password.PasswordEncoder encoder) {
        this.props = p; this.service = s; this.codec = c;
        this.usersFile = new com.agentlibrary.security.UsersFile(p.usersFile());
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) throws Exception {
        seedAdminIfMissing();
        seedInstallSkillIfMissing();
    }

    private void seedAdminIfMissing() throws Exception {
        var users = usersFile.load();
        if (!users.isEmpty()) return;
        String pw = randomPassword();
        String hash = encoder.encode(pw);
        usersFile.save(List.of(new User("admin", hash, Set.of("ADMIN"))));
        System.out.println("=== AGENT LIBRARY FIRST-RUN ADMIN PASSWORD ===");
        System.out.println("Username: admin");
        System.out.println("Password: " + pw);
        System.out.println("=== SAVE THIS. It will not be shown again. ===");
    }

    private void seedInstallSkillIfMissing() throws Exception {
        if (service.get(ArtifactType.SKILL, "agent-library-install", "HEAD").isPresent()) return;
        var resolver = new PathMatchingResourcePatternResolver();
        var skillMd = resolver.getResource("classpath:skills-seed/agent-library-install/skill.md");
        if (!skillMd.exists()) return;

        String source;
        try (InputStream in = skillMd.getInputStream()) {
            source = new String(in.readAllBytes());
        }
        var split = codec.splitFrontmatter(source);
        var meta = codec.decode(split.yaml());

        Map<String, byte[]> extras = new LinkedHashMap<>();
        for (String relative : new String[] {
            "scripts/agent-lib.sh", "scripts/agent-lib-common.sh", "README.md"
        }) {
            var res = resolver.getResource("classpath:skills-seed/agent-library-install/" + relative);
            if (res.exists()) {
                try (var in = res.getInputStream()) {
                    extras.put(relative, in.readAllBytes());
                }
            }
        }

        service.save(new Artifact(meta, split.body(), extras),
                     "bootstrap install skill",
                     new User("system", "", Set.of("ADMIN")), false);
    }

    private String randomPassword() {
        var r = new SecureRandom();
        byte[] buf = new byte[18];
        r.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
```

- [ ] **Step 2: Ensure `skills-seed/` is on the classpath**

In `pom.xml` inside `<build>`:

```xml
<resources>
    <resource><directory>src/main/resources</directory></resource>
    <resource><directory>.</directory>
        <includes>
            <include>skills-seed/**</include>
        </includes>
    </resource>
</resources>
```

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(storage): Bootstrapper seeds admin + install skill on first run"
```

---

## Milestone 10 — Docker Packaging

### Task 34: `Dockerfile`

**Files:**
- Create: `Dockerfile`

- [ ] **Step 1: Write multi-stage Dockerfile**

```dockerfile
# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw -B dependency:go-offline
COPY src src
COPY skills-seed skills-seed
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache bash git curl jq tar
WORKDIR /app
COPY --from=build /src/target/agent-library-*.jar /app/app.jar
COPY scripts/gen-password-hash.sh /app/scripts/gen-password-hash.sh
RUN chmod +x /app/scripts/gen-password-hash.sh

ENV SPRING_PROFILES_ACTIVE=prod \
    LIBRARY_REPO_PATH=/data/library.git \
    LIBRARY_WORK_PATH=/data/library-work \
    LIBRARY_USERS_FILE=/data/users.yaml

VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O - http://localhost:8080/healthz || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: `application-prod.yml`**

Create `src/main/resources/application-prod.yml`:

```yaml
app:
  repoPath: ${LIBRARY_REPO_PATH:/data/library.git}
  workPath: ${LIBRARY_WORK_PATH:/data/library-work}
  usersFile: ${LIBRARY_USERS_FILE:/data/users.yaml}
  serverName: agent-library
  serverEmail: agent-library@localhost
logging:
  level:
    com.agentlibrary: INFO
```

- [ ] **Step 3: Commit**

```bash
git add Dockerfile src/main/resources/application-prod.yml
git commit -m "chore(docker): multi-stage Dockerfile + prod profile"
```

### Task 35: `docker-compose.yml`

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: Write**

```yaml
services:
  agent-library:
    build: .
    image: agent-library:latest
    container_name: agent-library
    ports: ["8080:8080"]
    volumes:
      - library-data:/data
    environment:
      SPRING_PROFILES_ACTIVE: prod
    restart: unless-stopped
volumes:
  library-data:
```

- [ ] **Step 2: Commit**

```bash
git add docker-compose.yml
git commit -m "chore(docker): docker-compose.yml with persistent volume"
```

### Task 36: Local smoke test

- [ ] **Step 1: Build and run**

```bash
docker compose build
docker compose up -d
docker compose logs | grep "FIRST-RUN ADMIN PASSWORD" -A 3
```
Expected: first-run admin password printed.

- [ ] **Step 2: Hit health endpoint**

```bash
curl -f http://localhost:8080/healthz
```
Expected: `{"status":"ok"}` (after login for other endpoints; healthz is public per `SecurityConfig`).

- [ ] **Step 3: Tear down**

```bash
docker compose down
```

- [ ] **Step 4: Commit if any fixes needed**

---

## Milestone 11 — E2E + CLI tests

### Task 37: bats tests for CLI

**Files:**
- Create: `test-cli/install-skill.bats`

- [ ] **Step 1: Sample test**

```bash
#!/usr/bin/env bats

setup() {
    export AGENT_LIB_CONFIG="$BATS_TEST_TMPDIR/cfg"
    export HOME="$BATS_TEST_TMPDIR/home"
    mkdir -p "$HOME"
    SCRIPTS="$BATS_TEST_DIRNAME/../skills-seed/agent-library-install/scripts"
    # Expect a running server at $AGENT_LIB_SERVER (started by caller)
}

@test "search returns at least one result" {
    run "$SCRIPTS/agent-lib.sh" search ""
    [ "$status" -eq 0 ]
}

@test "install skill into HOME" {
    run "$SCRIPTS/agent-lib.sh" install skill/agent-library-install --harness claude --dry-run
    [ "$status" -eq 0 ]
}
```

- [ ] **Step 2: Commit**

```bash
git add test-cli
git commit -m "test(cli): bats-core smoke tests for install"
```

### Task 38: Testcontainers E2E (Java)

**Files:**
- Create: `src/test/java/com/agentlibrary/e2e/EndToEndIT.java`

- [ ] **Step 1: Test**

```java
package com.agentlibrary.e2e;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class EndToEndIT {

    @Container
    static GenericContainer<?> container =
        new GenericContainer<>(new ImageFromDockerfile().withDockerfile(java.nio.file.Path.of("Dockerfile")))
            .withExposedPorts(8080)
            .withStartupTimeout(Duration.ofMinutes(3));

    @Test
    void healthzIsReachable() throws Exception {
        String url = "http://" + container.getHost() + ":" + container.getMappedPort(8080) + "/healthz";
        HttpResponse<String> r = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("ok");
    }
}
```

- [ ] **Step 2: Run**

Run: `./mvnw verify -Dtest=EndToEndIT`

- [ ] **Step 3: Commit**

```bash
git commit -am "test(e2e): Testcontainers smoke test against built image"
```

---

## Milestone 12 — README + ops docs

### Task 39: Root README

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write** (only if user wants — spec doesn't explicitly require it; skip if no request).

If creating, cover: quickstart, `docker compose up`, first-run password behaviour, adding users, reverse-proxy note, CLI bootstrap (`agent-lib login`, `agent-lib install skill/agent-library-install`).

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: README with quickstart and CLI usage"
```

---

## Final Verification

- [ ] **Run full test suite**

Run: `./mvnw verify`
Expected: all tests green, image builds, E2E passes.

- [ ] **Manual UI walkthrough** (start server, log in, browse, create skill, install via CLI, confirm files placed under `$HOME/.claude/skills/`).

- [ ] **Push branch, open PR**

```bash
git push -u origin HEAD
gh pr create --title "Agent library server v0.1" --body "Implements docs/superpowers/specs/2026-04-19-agent-library-design.md"
```

---

## Coverage Notes & Deferred

- **Monaco editor integration** — textarea used for v1; wire Monaco as polish later.
- **Version diff viewer** — implied by spec but low-priority UI feature; add after core is green.
- **User management UI** (admin page CRUD for users) — list-only in v1; extend when first admin task arises.
- **Tag autocomplete UI** — HTMX endpoint `/api/v1/tags` exists; wire to an `<input list>` in later polish.
- **Remote git backend (`RemoteGitArtifactRepository`)** — interface is ready; phase C.
