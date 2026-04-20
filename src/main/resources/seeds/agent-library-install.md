---
name: agent-library-install
type: skill
version: 1.0.0
description: Skill for searching and installing artifacts from the Agent Library
harnesses:
  - claude
  - copilot
tags:
  - library
  - install
  - cli
category: developer-tools
author: system
install:
  target: ~/.claude/skills/agent-library-install/
  files:
    - skill.md
---
# Agent Library Install Skill

You have access to the Agent Library — a shared repository of skills, agents, MCP configs, and other artifacts maintained by your team.

## Usage

Use the `agent-lib` CLI to search, browse, and install artifacts from the library.

### Environment Setup

The CLI requires `AGENT_LIB_SERVER` to be set to your library server URL:

```bash
export AGENT_LIB_SERVER=http://your-server:8080
```

### Commands

**Search for artifacts:**
```bash
agent-lib search "git helper"
agent-lib search "deploy" --type skill
```

**List all available artifacts:**
```bash
agent-lib list
agent-lib list --type agent-claude --harness claude
```

**Get detailed info:**
```bash
agent-lib info my-skill
```

**Install an artifact:**
```bash
agent-lib install my-skill --harness claude
agent-lib install dev-team --dry-run
```

**Download raw artifact:**
```bash
agent-lib pull my-skill
```

**Upload a new artifact:**
```bash
agent-lib push ./my-new-agent.md
```

## Tips

- Use `--dry-run` with install to preview what would be placed
- Agent-group slugs install all members in role order (manager → analyse → implementation)
- All commands support `--help` for detailed usage information
