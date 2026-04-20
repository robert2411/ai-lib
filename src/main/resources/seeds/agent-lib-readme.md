---
name: agent-lib-readme
type: skill
version: 1.0.0
description: Agent Library CLI documentation and usage guide
harnesses:
  - claude
  - copilot
tags:
  - library
  - documentation
category: developer-tools
author: system
---
# Agent Library CLI — README

## Overview

The Agent Library CLI (`agent-lib`) provides command-line access to your team's shared artifact library. It enables searching, installing, and publishing skills, agents, MCP configurations, and other artifacts.

## Prerequisites

- **curl** — HTTP client for API communication
- **jq** — JSON processor for response formatting
- **unzip** or **python3** — for archive extraction during install

## Configuration

Set the following environment variables:

| Variable | Required | Description |
|----------|----------|-------------|
| `AGENT_LIB_SERVER` | Yes | Library server base URL |
| `AGENT_LIB_USER` | No | HTTP basic auth username |
| `AGENT_LIB_PASS` | No | HTTP basic auth password |

## Commands

| Command | Description |
|---------|-------------|
| `install <slug>` | Download and install artifact(s) |
| `search <query>` | Search by keyword |
| `list` | List all artifacts |
| `info <slug>` | Show artifact metadata |
| `pull <slug>` | Download raw artifact file |
| `push <file>` | Upload local artifact |

## Agent Groups

Agent groups bundle multiple agents together. Installing a group installs all members in role order:

1. **manager** — orchestration agent
2. **analyse** — analysis and planning agent
3. **implementation** — coding and execution agent

```bash
# Preview what a group install would do
agent-lib install my-team --dry-run

# Install all members
agent-lib install my-team --harness claude
```

## Verification

Run the built-in self-test to verify prerequisites:

```bash
agent-lib --self-test
```
