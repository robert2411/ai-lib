---
id: TASK-0006
title: Milestone 6 — Auth and Password Tooling
status: Done
assignee: []
created_date: '2026-04-19 20:03'
updated_date: '2026-04-19 23:03'
labels:
  - milestone
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
File-backed user security: UsersFile loader, FileUserDetailsService wired into Spring Security, LoginRateLimiter via bucket4j, BcryptCli + gen-password-hash.sh script.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
✅ SECURITY APPROVED — static audit complete, zero vulnerabilities identified

- Files reviewed:
  - src/main/java/com/agentlibrary/auth/UsersFile.java
  - src/main/java/com/agentlibrary/auth/FileUserDetailsService.java
  - src/main/java/com/agentlibrary/auth/LoginRateLimiter.java
  - src/main/java/com/agentlibrary/config/SecurityConfig.java
  - src/main/java/com/agentlibrary/config/LoginSuccessHandler.java
  - src/main/java/com/agentlibrary/config/RateLimitConfig.java
  - src/main/java/com/agentlibrary/config/RateLimitFilter.java
  - src/main/java/com/agentlibrary/cli/BcryptCli.java
  - scripts/gen-password-hash.sh
  - src/main/resources/application.yml
  - src/main/resources/application-prod.yml
  - src/main/java/com/agentlibrary/storage/AppProperties.java

- Checks performed: OWASP Top 10, path traversal, ReDoS, input validation

- Key findings (all PASS):
  1. YAML Injection (CVE-2022-1471): SafeConstructor correctly used (UsersFile.java:73)
  2. Password Storage: BCrypt via BCryptPasswordEncoder — no plaintext anywhere
  3. Sensitive Data: data/ in .gitignore; no secrets in source; no credential logging
  4. Access Control: Role hierarchy (ADMIN>EDITOR>USER) properly enforced
  5. Rate Limiting: bucket4j per-IP with 10 req/10min; memory growth acknowledged via TODO
  6. CSRF disabled: Acceptable for HTTP Basic API server (by design)
  7. X-Forwarded-For trust: Documented accepted risk for reverse-proxy deployment model
  8. No injection sinks, no ReDoS patterns, no path traversal from user input
<!-- SECTION:NOTES:END -->
