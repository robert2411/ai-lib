# Agent Library Server

A self-hosted library server for AI agent artifacts — skills, agents, MCP server configs, commands, hooks, prompts, and more. A small team uploads, browses, and installs these artifacts into their local harnesses (Claude, Copilot) through a web UI, REST API, and companion CLI.

## Quickstart

```bash
# Clone and start
git clone <repo-url> && cd ai-library
docker compose up -d

# Access the web UI
open http://localhost:8080

# Default login
#   Username: admin
#   Password: changeme  ← change immediately!
```

The server initialises on first run: creates the git repository, working checkout, and a default `admin` user. Change the default password immediately via the `users.yaml` file (see [Users Configuration](#users-configuration) below).

## Configuration Reference

All configuration is via environment variables, set in `docker-compose.yml` or passed with `docker run -e`:

| Variable | Default | Description |
|----------|---------|-------------|
| `LIBRARY_DATA_DIR` | `/data` | Root data directory for all persistent state |
| `LIBRARY_REPO_PATH` | `/data/library.git` | Path to the bare git repository |
| `LIBRARY_WORK_PATH` | `/data/library-work` | Path to the working checkout |
| `LIBRARY_USERS_FILE` | `/data/users.yaml` | Path to the users configuration file |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile (`prod` for production) |

### docker-compose.yml

```yaml
services:
  agent-library:
    build: .
    image: agent-library:latest
    ports:
      - "8080:8080"
    volumes:
      - library-data:/data
    environment:
      SPRING_PROFILES_ACTIVE: prod
      LIBRARY_DATA_DIR: /data
      LIBRARY_REPO_PATH: /data/library.git
      LIBRARY_WORK_PATH: /data/library-work
      LIBRARY_USERS_FILE: /data/users.yaml
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3

volumes:
  library-data:
    name: agent-library-data
```

## Users Configuration

Users are managed in a YAML file (`/data/users.yaml`). Passwords are stored as BCrypt hashes.

### Format

```yaml
users:
  - username: admin
    password: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    roles:
      - ADMIN
      - EDITOR
      - USER
  - username: alice
    password: "$2a$10$..."
    roles:
      - EDITOR
      - USER
```

**Roles:** `ADMIN` (full access), `EDITOR` (create/edit artifacts), `USER` (read-only + install).

### Generating Password Hashes

Use the bundled script to generate BCrypt hashes:

```bash
# From the project root (requires Maven):
./scripts/gen-password-hash.sh mypassword

# Interactive (prompts for password):
./scripts/gen-password-hash.sh

# Inside the container:
docker exec agent-library /app/scripts/gen-password-hash.sh mypassword
```

Paste the output into `users.yaml` and restart the container.

## CLI Usage

The companion CLI (`scripts/agent-lib.sh`) interacts with the library server from your terminal.

### Setup

```bash
export AGENT_LIB_SERVER=http://localhost:8080
export AGENT_LIB_USER=admin
export AGENT_LIB_PASS=changeme

# Verify connectivity
./scripts/agent-lib.sh --self-test
```

### Commands

```bash
# Search artifacts by keyword
agent-lib search "git helper"
agent-lib search "deploy" --type skill

# List all artifacts (with optional filters)
agent-lib list
agent-lib list --type skill
agent-lib list --harness claude

# Show artifact metadata
agent-lib info my-skill

# Install an artifact into your harness
agent-lib install my-skill                      # defaults to claude harness
agent-lib install my-skill --harness copilot
agent-lib install dev-team --dry-run            # preview group install

# Download raw artifact file
agent-lib pull my-skill
agent-lib pull my-skill --version 1.2.0

# Upload a local artifact file
agent-lib push ./my-agent.md
agent-lib push ./skills/git-helper.md
```

**Prerequisites:** `curl`, `jq`, `unzip` (or `python3`), Bash 3.2+.

## Artifact Types

| Type | Slug | Repo Folder | Description |
|------|------|-------------|-------------|
| Skill | `skill` | `skills/` | Claude skill files |
| Agent (Claude) | `agent-claude` | `agents-claude/` | Claude agent definitions |
| Agent (Copilot) | `agent-copilot` | `agents-copilot/` | GitHub Copilot agent definitions |
| Agent Group | `agent-group` | `agent-groups/` | Ordered group of agents for team install |
| MCP Server | `mcp` | `mcp/` | MCP server configurations |
| Command | `command` | `commands/` | Claude slash commands |
| Hook | `hook` | `hooks/` | Claude hook configurations |
| Claude MD | `claude-md` | `claude-md/` | CLAUDE.md snippets |
| OpenCode | `opencode` | `opencode/` | OpenCode configurations |
| Prompt | `prompt` | `prompts/` | Reusable prompt templates |

## Harness Path Matrix

When you install an artifact, the CLI places it at the correct target path based on the artifact type and target harness. The table below shows where each type is installed:

| Artifact Type | Claude | Copilot |
|---------------|--------|---------|
| skill | `~/.claude/skills/{name}/` | — |
| agent-claude | `.claude/agents/{name}.md` | — |
| agent-copilot | — | `.github/agents/{name}.md` |
| mcp | `.claude/settings.json#mcpServers.{name}` | `.vscode/mcp.json#servers.{name}` |
| command | `~/.claude/commands/{name}.md` | — |
| hook | `.claude/settings.json#hooks.{name}` | — |
| claude-md | `CLAUDE.md` | — |
| prompt | `~/.claude/prompts/{name}.md` | — |
| opencode | *(metadata override only)* | — |
| agent-group | *(expands to member entries)* | *(expands to member entries)* |

**Notes:**
- Paths with `~` are user-global (home directory). Paths without `~` are project-relative.
- Paths containing `#` indicate JSON merge targets (the file is merged, not overwritten).
- Agent groups expand to their ordered member list; each member uses its own type's path.
- Individual artifacts can override target paths via `install.target` in their metadata.

## API Summary

All endpoints require authentication (HTTP Basic for API/CLI, session cookie for browser).

### REST API (`/api/v1`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/artifacts` | List artifacts (filter: `type`, `harness`, `tag`, `q`) |
| `GET` | `/artifacts/{type}/{name}` | Get latest metadata + content |
| `GET` | `/artifacts/{type}/{name}/versions` | List versions |
| `GET` | `/artifacts/{type}/{name}@{ver}` | Get specific version |
| `POST` | `/artifacts/{type}` | Create artifact |
| `PUT` | `/artifacts/{type}/{name}` | Update artifact |
| `DELETE` | `/artifacts/{type}/{name}` | Delete artifact |
| `GET` | `/artifacts/{type}/{name}/bundle` | Download artifact bundle (zip) |
| `GET` | `/install/manifest` | Get install manifest for slugs + harness |
| `GET` | `/install/bundle` | Download install bundle (zip) |
| `GET` | `/artifacts/search` | Full-text search |
| `GET` | `/tags` | List all tags |
| `GET` | `/harnesses` | List harnesses |
| `GET` | `/types` | List artifact types |
| `POST` | `/admin/reindex` | Rebuild search index |
| `GET` | `/actuator/health` | Health check |

## Development

### Prerequisites

- Java 21 (Temurin recommended)
- Maven (via included wrapper `./mvnw`)
- Git

### Build & Test

```bash
# Run tests
./mvnw test

# Build without tests
./mvnw package -DskipTests

# Run locally (dev profile)
./mvnw spring-boot:run
```

### Project Structure

```
src/
├── main/java/com/agentlibrary/
│   ├── AgentLibraryApplication.java   # Spring Boot entry point
│   ├── auth/                          # Authentication & user management
│   ├── bootstrap/                     # First-run initialization
│   ├── cli/                           # CLI utilities (BcryptCli)
│   ├── config/                        # Spring configuration
│   ├── index/                         # Lucene search index
│   ├── install/                       # Install manifest & bundle logic
│   ├── metadata/                      # YAML frontmatter parsing
│   ├── model/                         # Domain model (ArtifactType, Harness, etc.)
│   ├── service/                       # Core business logic
│   ├── storage/                       # Git-backed artifact repository
│   └── web/                           # Controllers (web UI + REST API)
├── main/resources/
│   └── templates/                     # Thymeleaf templates
└── test/                              # Unit & integration tests
scripts/
├── agent-lib.sh                       # CLI client
├── agent-lib-common.sh                # CLI shared helpers
└── gen-password-hash.sh               # BCrypt hash generator
```

### Architecture

Single Spring Boot 3 application: Thymeleaf server-rendered views + HTMX for partial swaps, JGit for git storage, in-memory Lucene for search. Runs in one container; a persistent volume holds the bare repo, working checkout, and users file.

## Contributing

1. **Fork & branch** — Create a feature branch from `main`.
2. **Follow conventions** — Java code follows standard Spring Boot patterns. Keep commits focused.
3. **Test** — Write unit tests for new code. Run `./mvnw test` before submitting.
4. **PR description** — Explain what changed and why. Reference any related issues.
5. **Review** — All changes require review before merge.

### Artifact Contributions

To contribute a new artifact to the library:

1. Create a markdown/YAML file with valid frontmatter (see existing artifacts for format).
2. Upload via CLI: `agent-lib push ./my-artifact.md`
3. Or use the web UI: navigate to `/new` and fill in the form.

## License

See [LICENSE](LICENSE) for details.
