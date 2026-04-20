# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-04-19

### Added

- Core artifact storage backed by local bare git repository (JGit)
- Typed artifacts: skill, agent-claude, agent-copilot, agent-group, mcp, command, hook, claude-md, opencode, prompt
- Explicit semver versioning with git tags
- Full-text search via in-memory Lucene index
- Web UI with Thymeleaf + HTMX (browse, create, edit, delete, search)
- REST API (`/api/v1`) for CRUD, search, install manifest, and bundle download
- Install manifest resolver with harness-specific target paths (Claude, Copilot)
- Agent-group expansion for team installs (ordered member list by role)
- CLI client (`agent-lib.sh`) — install, search, list, pull, push, info
- File-based user authentication (`users.yaml` with BCrypt passwords)
- Docker single-image deployment (Alpine + Temurin 21)
- First-run bootstrap (auto-init git repo + default admin user)
- Health check endpoint (`/actuator/health`)
- Password hash generator script (`gen-password-hash.sh`)
