---
id: doc-0001
title: Agent Library Server — Design
type: other
created_date: '2026-04-19 19:32'
---
# Agent Library Server — Design

**Date:** 2026-04-19
**Status:** Approved, ready for planning
**Source of truth:** `docs/superpowers/specs/2026-04-19-agent-library-design.md` (committed to git)

## Purpose

A library server for agent artifacts — Claude skills, Claude/Copilot agents, MCP server configs, slash commands, hooks, `CLAUDE.md` snippets, OpenCode configs, prompts. A small team (<10 users) uploads, browses, and installs these artifacts into their local harnesses through a Claude skill + companion CLI.

## Scope

**First release:**

- Team auth (basic, file-based users, <10 known users)
- Local bare git repo as storage backend (abstraction allows remote-git later)
- Typed artifacts, each in its own folder in the git repo
- Tagging: harness list + free-form tags + structured facets (category, language, author, visibility)
- Explicit semver per artifact, enforced, materialised as git tags
- Full CRUD web UI (browse, upload, edit in-browser, delete, manage tags)
- Install skill + shell CLI, supporting auto-place and fetch-only modes
- Git-only metadata (YAML frontmatter / sidecar + generated `INDEX.yaml`), no DB
- Docker single-image deployment

**Explicitly out of first release:** public/unauthenticated access, remote git backend (phase C), OIDC/2FA, per-artifact ACLs, external search service.

## Architecture

Single Spring Boot 3 application. Thymeleaf server-rendered views + HTMX for partial swaps. JGit for git storage. In-memory Lucene (RAMDirectory) for search. Runs in one container; persistent volume holds the bare repo, working checkout, users file, and config overrides.

```
Browser (HTMX)  ──HTTP──►  Spring Boot
                             ├─ Controllers (web + API)
                             ├─ ArtifactService
                             ├─ IndexService (in-memory)
                             ├─ InstallService
                             └─ ArtifactRepository ── JGit ── /data/library.git
```

Storage abstracted behind `ArtifactRepository`. `LocalGitArtifactRepository` for v1; `RemoteGitArtifactRepository` (phase C) will clone and push to an external remote with no contract change.

## Git Repository Layout

Bare repo on volume. Working checkout separate. One folder per artifact type; one folder per artifact inside.

```
library.git/
  skills/<name>/skill.md
  agents-claude/<name>/agent.md
  agents-copilot/<name>/agent.md
  mcp/<name>/{mcp.json, meta.yaml}
  commands/<name>/command.md
  hooks/<name>/{hook.json, meta.yaml}
  claude-md/<name>/snippet.md
  opencode/<name>/{config.yaml, meta.yaml}
  prompts/<name>/prompt.md
  INDEX.yaml          # auto-generated
```

Artifact identity: `(type, name)`. `name` is a URL-safe slug matching the folder. Metadata lives in YAML frontmatter when the primary file is markdown, otherwise in `meta.yaml`. Version tags are lightweight: `<type>/<name>@<semver>`.

## Artifact Metadata Schema

Common fields:

```yaml
name: git-helper
title: Git Helper
type: skill               # skill|agent-claude|agent-copilot|mcp|command|hook|claude-md|opencode|prompt
version: 1.2.0
description: One-line summary
harnesses: [claude, copilot]
tags: [git, workflow]
category: developer-tools
language: en
author: robert
visibility: team          # team|private (future-proof)
created: 2026-04-19T10:00:00Z
updated: 2026-04-19T10:00:00Z
install:
  target: ~/.claude/skills/{name}/
  files: [skill.md, files/**]
  merge: null             # MCP/hooks: json-merge strategy
```

Per-type extensions allowed (MCP adds `serverName`, hooks add `event`). `INDEX.yaml` is a flat list of all metadata, regenerated on every commit, rebuildable via `POST /admin/reindex`.

## Storage Abstraction

```java
interface ArtifactRepository {
  List<Artifact> list(Filter f);
  Optional<Artifact> get(String type, String name, String ref);
  CommitResult save(Artifact a, String message, User u);
  void delete(String type, String name, String message, User u);
  List<VersionRef> versions(String type, String name);
  InputStream bundle(String type, String name, String ref);
}
```

**Write flow:** validate → stage → commit (`"<type>/<name>: <action> by <user>"`, author = principal, committer = server) → tag if version bumped → regenerate `INDEX.yaml` → notify `IndexService`. Writes serialised through a single-writer queue.

## REST API (`/api/v1`)

```
GET    /artifacts                                list; filter: type, harness, tag, q
GET    /artifacts/{type}/{name}                  latest metadata + content
GET    /artifacts/{type}/{name}/versions
GET    /artifacts/{type}/{name}@{ver}            specific version
POST   /artifacts/{type}                         create
PUT    /artifacts/{type}/{name}                  update
DELETE /artifacts/{type}/{name}                  remove folder
GET    /artifacts/{type}/{name}/bundle?ref=…     tar.gz
GET    /artifacts/{type}/{name}/install-manifest?harness=claude
GET    /tags   /harnesses   /types
POST   /admin/reindex
GET    /healthz
```

Auth: HTTP Basic for API/CLI, session cookie for browser. All endpoints require auth.

## Web UI

Pages: `/`, `/browse`, `/a/{type}/{name}`, `/new`, `/edit/{type}/{name}`, `/admin`, `/login`.

HTMX handles: debounced live search, filter chips, tag autocomplete, inline save, on-demand version diff. Fragments served via `th:fragment`. Monaco loaded as static asset — no frontend build step.

## Auth

Spring Security + file-backed users in `/data/users.yaml` (bcrypt hashes). Roles: `USER`, `ADMIN`. Session cookie for browser, HTTP Basic for CLI/API. No self-signup. Rate-limit `/login` via bucket4j. Ship `scripts/gen-password-hash.sh` to generate bcrypt hashes without running the full app.

Deploy behind TLS reverse proxy (Caddy/Traefik) — documented, not bundled.

Future (not v1): OIDC, per-artifact ACLs, separate audit log.

## Install Skill + CLI

Hosted in the library itself — bootstrap commit seeds `skills/agent-library-install/` with `skill.md`, `scripts/agent-lib.sh`, `scripts/agent-lib-common.sh`, `README.md`.

**CLI:**

```
agent-lib search <query> [--type T] [--harness H] [--tag T]
agent-lib show <type>/<name>[@ver]
agent-lib install <type>/<name>[@ver] [--harness H] [--fetch-only] [--dest DIR]
agent-lib list-versions <type>/<name>
agent-lib login
agent-lib config set server <url>
```

**Install:** fetch manifest → fetch bundle → resolve `targetPath` using harness defaults (claude skill → `~/.claude/skills/<name>/`, claude agent → `.claude/agents/<name>.md`, copilot agent → `.github/agents/<name>.md`, MCP → merge into `settings.json` at `mcpServers.<name>`, hook → merge into `settings.json` at `hooks.<event>[]`, claude-md → append to nearest `CLAUDE.md`). `--fetch-only` writes to `./artifact/`. Dry-run prints planned actions. Skill delegates to CLI, confirms destructive merges.

Runtime deps: `curl`, `jq`, `tar`, Bash 3.2+.

## Versioning & Tags

- `version` (semver) is source of truth.
- New version must be strictly greater than latest (admin `--force` overrides).
- Server creates lightweight tag `<type>/<name>@<version>`.
- Install ref resolution: `name` → latest, `name@1.2.0` → exact, `name@^1.0` → range, `name@HEAD` → main tip.
- Rollback: new commit restoring old tree + version bump. Never rewrites history.
- Delete: commit removes folder; tags remain. Names unique across repo lifetime.

## Search

`IndexService` holds metadata in memory, rebuilt from `INDEX.yaml` on startup and after each commit. Lucene `RAMDirectory` for full-text on `title + description + content-head` and faceted filters (`type/harness/tag/category/language/author`). Not persisted. <10k artifacts expected.

## Docker Deployment

Multi-stage build: Maven + Temurin 21. Runtime: `eclipse-temurin:21-jre-alpine` + `git`, `bash`, `curl`, `jq`.

**Container layout:**

```
/app/app.jar
/app/scripts/gen-password-hash.sh
/data/library.git
/data/library-work/
/data/users.yaml
/data/config/
```

**docker-compose:**

```yaml
services:
  agent-library:
    image: agent-library:latest
    ports: ["8080:8080"]
    volumes: ["library-data:/data"]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      LIBRARY_REPO_PATH: /data/library.git
      LIBRARY_WORK_PATH: /data/library-work
      LIBRARY_USERS_FILE: /data/users.yaml
    restart: unless-stopped
volumes:
  library-data:
```

**First-run bootstrap:** if `/data/library.git` missing, `git init --bare` and seed with empty `INDEX.yaml` plus the `agent-library-install` skill (self-hosting). Users file created with a single admin account; generated password printed once to the container log.

## Testing

- **Unit:** metadata validation, semver compare, install-manifest resolver, JSON merge.
- **Integration (`@SpringBootTest` + tmp git dir):** full save → index → search → bundle → install-manifest.
- **Repo layer:** JGit against tmp bare repo — commit races, tag creation, tree walking.
- **Web:** `MockMvc` for controllers + HTMX fragments.
- **CLI:** `bats-core` against an ephemeral server container.
- **E2E:** Testcontainers spins up built image; shell tests install a real skill and verify files on disk.

No mocked git. Real JGit, real tmp directories.

## Open Items

None blocking. Deferred: remote git backend (phase C), OIDC / per-artifact ACLs / audit log, public browse / moderation, multi-instance HA (single-writer queue implies single process).
