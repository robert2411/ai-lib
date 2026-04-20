---
id: TASK-0009
title: Milestone 9 — Install Skill and CLI (seed content)
status: Done
assignee: []
created_date: '2026-04-19 20:04'
updated_date: '2026-04-20 00:37'
labels:
  - milestone
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
agent-lib.sh CLI with core subcommands (install, search, list, pull, push, info). Bootstrapper for first-run seed of the install skill into the library itself.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
✅ SECURITY APPROVED — static audit complete, zero exploitable vulnerabilities identified in M9 scope

- Files reviewed:
  - scripts/agent-lib.sh (CLI dispatch, install, pull, push, search, list, info)
  - scripts/agent-lib-common.sh (HTTP helpers, zip extraction, JSON merge, harness resolution)
  - src/main/java/com/agentlibrary/bootstrap/Bootstrapper.java
  - src/main/java/com/agentlibrary/install/InstallEntry.java
  - src/main/java/com/agentlibrary/install/InstallManifestResolver.java
  - src/main/java/com/agentlibrary/install/BundleService.java
  - src/main/java/com/agentlibrary/model/InstallConfig.java
  - src/main/java/com/agentlibrary/metadata/MetadataValidator.java
  - src/main/java/com/agentlibrary/metadata/MetadataCodec.java
  - src/main/java/com/agentlibrary/metadata/TypeSchemaRegistry.java
  - src/main/java/com/agentlibrary/web/api/InstallApiController.java
  - src/main/resources/seeds/ (all seed files)

- Checks performed: OWASP Top 10, path traversal, ReDoS, input validation, shell injection, zip-slip, YAML deserialization

- Key mitigations confirmed:
  1. YAML: SafeConstructor used in MetadataCodec, IndexFile, UsersFile (no arbitrary object instantiation)
  2. Slug validation: MetadataValidator enforces ^[a-z][a-z0-9-]*[a-z0-9]$ — prevents path traversal via {name} substitution
  3. Zip-slip: BundleService.sanitizeEntryName() rejects ../, absolute paths, and backslash-encoded traversal
  4. Shell injection: CLI uses proper quoting; search query is URI-encoded via jq @uri
  5. TypeSchemaRegistry uses new Yaml() but ONLY on classpath resources (enum-controlled paths) — not user input

- Noted design observations (not findings — require trust model change to become exploitable):
  - install.target metadata override is not path-validated server-side, but slug regex on name prevents traversal via template substitution. The overall install path comes from HARNESS_TARGET_PATHS (hardcoded safe paths) or metadata.install().target() which requires authenticated upload. Current single-author trust model is acceptable; if multi-tenant public registry is planned, add server-side path validation to InstallConfig.target.
  - CLI _cmd_pull filename derived from slug (safe) with fallback to primaryFileName from server — acceptable in trusted-server model.
<!-- SECTION:NOTES:END -->
