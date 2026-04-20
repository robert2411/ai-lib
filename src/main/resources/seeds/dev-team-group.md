---
name: dev-team-group
type: agent-group
version: 1.0.0
description: Example agent group demonstrating role-based member structure
harnesses:
  - claude
  - copilot
tags:
  - example
  - agent-group
category: developer-tools
author: system
members:
  - slug: agent-lib-sh
    role: manager
  - slug: agent-lib-common-sh
    role: analyse
  - slug: agent-lib-readme
    role: implementation
---
# Dev Team Group

This is an example agent-group artifact demonstrating the role-based member structure.

## Members

| Role | Slug | Description |
|------|------|-------------|
| manager | agent-lib-sh | Orchestration and coordination |
| analyse | agent-lib-common-sh | Analysis and planning |
| implementation | agent-lib-readme | Coding and execution |

## Usage

Install all members of this group in role order:

```bash
agent-lib install dev-team-group --harness claude
```

Preview what would be installed:

```bash
agent-lib install dev-team-group --dry-run
```
