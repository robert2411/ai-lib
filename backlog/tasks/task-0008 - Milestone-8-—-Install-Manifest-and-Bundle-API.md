---
id: TASK-0008
title: Milestone 8 — Install Manifest and Bundle API
status: Done
assignee: []
created_date: '2026-04-19 20:04'
updated_date: '2026-04-19 23:59'
labels:
  - milestone
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
InstallManifest and InstallManifestResolver for per-harness install path resolution. InstallApiController exposing manifest and bundle download endpoints.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
✅ SECURITY APPROVED — static audit complete, zero vulnerabilities identified
- Files reviewed: InstallEntry.java, InstallManifest.java, InstallManifestResolver.java, BundleService.java, InstallApiController.java, ArtifactService.java, LocalGitArtifactRepository.java (bundle method), SecurityConfig.java, ApiExceptionHandler.java, MetadataValidator.java, Harness.java, InstallConfig.java
- Checks: OWASP Top 10, path traversal, zip-slip, ReDoS, input validation, resource exhaustion
- Zip-slip: MITIGATED — sanitizeEntryName() rejects ../ , absolute paths, and .. prefixes; test coverage confirms
- Path traversal via slugs: NOT POSSIBLE — slugs validated against in-memory index; SLUG_PATTERN [a-z][a-z0-9-]*[a-z0-9] enforced at creation
- Input validation: Harness enum rejects unknown values (400); unknown slugs throw NotFoundException (404)
- Auth: All GET /api/v1/** endpoints require authentication (SecurityConfig)
- Error handling: ApiExceptionHandler returns RFC 7807 ProblemDetail without internal details
<!-- SECTION:NOTES:END -->
