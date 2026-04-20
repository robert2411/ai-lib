---
name: agent-lib-sh
type: skill
version: 1.0.0
description: Agent Library CLI main script (agent-lib.sh) for searching and installing artifacts
harnesses:
  - claude
  - copilot
tags:
  - library
  - cli
  - bash
category: developer-tools
author: system
---
#!/usr/bin/env bash
# agent-lib.sh — CLI for the Agent Library
# Usage: agent-lib <command> [options]
# Requires: AGENT_LIB_SERVER env var set to server URL
set -euo pipefail

# Source common helpers from the same directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=agent-lib-common.sh
source "${SCRIPT_DIR}/agent-lib-common.sh"

# === Global Help ===

_usage() {
    cat <<'EOF'
Usage: agent-lib <command> [options]

Commands:
  install <slug>   Download and install an artifact (or agent-group members)
  search <query>   Search artifacts by keyword
  list             List all artifacts
  pull <slug>      Download raw artifact file
  push <file>      Upload a local artifact file
  info <slug>      Show artifact metadata

Options:
  --help, -h       Show this help message
  --self-test      Verify prerequisites and server connectivity

Environment:
  AGENT_LIB_SERVER   Library server URL (required)
  AGENT_LIB_USER     HTTP basic auth username (optional)
  AGENT_LIB_PASS     HTTP basic auth password (optional)

Examples:
  agent-lib search "git helper"
  agent-lib install my-skill --harness claude
  agent-lib list --type skill
  agent-lib push ./my-agent.md
EOF
}

# === Self-Test ===

_self_test() {
    local errors=0

    _info "Checking prerequisites..."

    for cmd in curl jq; do
        if command -v "$cmd" >/dev/null 2>&1; then
            _info "  ✓ $cmd found"
        else
            _info "  ✗ $cmd NOT found"
            errors=$((errors + 1))
        fi
    done

    if command -v unzip >/dev/null 2>&1; then
        _info "  ✓ unzip found"
    elif command -v python3 >/dev/null 2>&1; then
        _info "  ✓ python3 found (fallback for zip extraction)"
    else
        _info "  ✗ Neither unzip nor python3 found"
        errors=$((errors + 1))
    fi

    if [ -z "${AGENT_LIB_SERVER:-}" ]; then
        _info "  ✗ AGENT_LIB_SERVER not set"
        errors=$((errors + 1))
    else
        _info "  ✓ AGENT_LIB_SERVER=${AGENT_LIB_SERVER}"
        _info "Checking server connectivity..."
        if curl -sSf "${AGENT_LIB_SERVER}/api/v1/health" >/dev/null 2>&1; then
            _info "  ✓ Server reachable"
        else
            _info "  ✗ Server not reachable at ${AGENT_LIB_SERVER}"
            errors=$((errors + 1))
        fi
    fi

    if [ "$errors" -eq 0 ]; then
        _info "All checks passed."
        exit 0
    else
        _info "$errors check(s) failed."
        exit 1
    fi
}

# === Search Subcommand ===

_cmd_search() {
    if [ $# -eq 0 ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
        cat <<'EOF'
Usage: agent-lib search <query> [--type TYPE] [--harness HARNESS]

Search artifacts by keyword.

Options:
  --type TYPE        Filter by artifact type (skill, agent-claude, mcp, etc.)
  --harness HARNESS  Filter by harness (claude, copilot)
  --help, -h         Show this help

Examples:
  agent-lib search "git"
  agent-lib search "deploy" --type skill
EOF
        if [ $# -eq 0 ]; then exit 1; else exit 0; fi
    fi

    local query=""
    local type_filter=""
    local harness_filter=""

    while [ $# -gt 0 ]; do
        case "$1" in
            --type) type_filter="$2"; shift 2 ;;
            --harness) harness_filter="$2"; shift 2 ;;
            *) query="${query:+$query }$1"; shift ;;
        esac
    done

    _require_server
    _check_prerequisites

    local params
    params="q=$(printf '%s' "$query" | jq -sRr @uri)"
    if [ -n "$type_filter" ]; then
        params="${params}&type=${type_filter}"
    fi
    if [ -n "$harness_filter" ]; then
        params="${params}&harness=${harness_filter}"
    fi

    local response
    response="$(_api_get "artifacts/search?${params}")"

    printf '%-30s %-15s %s\n' "SLUG" "TYPE" "DESCRIPTION"
    printf '%-30s %-15s %s\n' "----" "----" "-----------"
    echo "$response" | jq -r '.[] | [.name, .type, (.description // "")] | @tsv' | while IFS=$'\t' read -r slug type desc; do
        printf '%-30s %-15s %s\n' "$slug" "$type" "$(_truncate "$desc" 40)"
    done
}

# === List Subcommand ===

_cmd_list() {
    if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
        cat <<'EOF'
Usage: agent-lib list [--type TYPE] [--harness HARNESS]

List all artifacts in the library.

Options:
  --type TYPE        Filter by artifact type (skill, agent-claude, mcp, etc.)
  --harness HARNESS  Filter by harness (claude, copilot)
  --help, -h         Show this help

Examples:
  agent-lib list
  agent-lib list --type skill
  agent-lib list --harness claude
EOF
        exit 0
    fi

    local type_filter=""
    local harness_filter=""

    while [ $# -gt 0 ]; do
        case "$1" in
            --type) type_filter="$2"; shift 2 ;;
            --harness) harness_filter="$2"; shift 2 ;;
            *) shift ;;
        esac
    done

    _require_server
    _check_prerequisites

    local params=""
    if [ -n "$type_filter" ]; then
        params="type=${type_filter}"
    fi
    if [ -n "$harness_filter" ]; then
        params="${params:+$params&}harness=${harness_filter}"
    fi

    local page=0
    local size=50
    local has_more=true

    printf '%-30s %-15s %s\n' "SLUG" "TYPE" "HARNESSES"
    printf '%-30s %-15s %s\n' "----" "----" "---------"

    while [ "$has_more" = "true" ]; do
        local page_params="${params:+$params&}page=${page}&size=${size}"
        local response
        response="$(_api_get "artifacts?${page_params}")"

        local count
        count="$(echo "$response" | jq -r '.content | length')"

        echo "$response" | jq -r '.content[] | [.name, .type, (.harnesses | join(","))] | @tsv' | while IFS=$'\t' read -r slug type harnesses; do
            printf '%-30s %-15s %s\n' "$slug" "$type" "$harnesses"
        done

        if [ "$count" -lt "$size" ]; then
            has_more=false
        else
            page=$((page + 1))
        fi
    done
}

# === Info Subcommand ===

_cmd_info() {
    if [ $# -eq 0 ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
        cat <<'EOF'
Usage: agent-lib info <slug>

Show detailed metadata for an artifact.

Options:
  --help, -h    Show this help

Examples:
  agent-lib info my-skill
EOF
        if [ $# -eq 0 ]; then exit 1; else exit 0; fi
    fi

    local slug="$1"

    _require_server
    _check_prerequisites

    local response
    response="$(_api_get "artifacts/${slug}/latest")"

    echo "$response" | jq -r '
        "Name:        " + .name,
        "Type:        " + .type,
        "Version:     " + .version,
        "Description: " + (.description // ""),
        "Author:      " + (.author // ""),
        "Harnesses:   " + (.harnesses | join(", ")),
        "Tags:        " + (.tags | join(", ")),
        "Category:    " + (.category // "")
    '
}

# === Install Subcommand ===

_cmd_install() {
    if [ $# -eq 0 ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
        cat <<'EOF'
Usage: agent-lib install <slug> [options]

Install an artifact (or all members of an agent-group).

Options:
  --harness HARNESS  Target harness (default: claude)
  --version VERSION  Specific version to install (default: latest)
  --dry-run          Show what would be installed without writing files
  --fetch-only       Download and extract without placing files
  --dest DIR         Destination for --fetch-only (default: ./artifact/)
  --help, -h         Show this help

Examples:
  agent-lib install my-skill
  agent-lib install my-skill --harness copilot
  agent-lib install dev-team --dry-run
EOF
        if [ $# -eq 0 ]; then exit 1; else exit 0; fi
    fi

    local slug="$1"; shift
    local harness=""
    local version=""
    local dry_run=false
    local fetch_only=false
    local dest="./artifact/"

    while [ $# -gt 0 ]; do
        case "$1" in
            --harness) harness="$2"; shift 2 ;;
            --version) version="$2"; shift 2 ;;
            --dry-run) dry_run=true; shift ;;
            --fetch-only) fetch_only=true; shift ;;
            --dest) dest="$2"; shift 2 ;;
            *) _die "Unknown option: $1" ;;
        esac
    done

    harness="$(_resolve_harness "$harness")"

    _require_server
    _check_prerequisites

    # Step A: Fetch manifest
    local manifest_params="slugs=${slug}&harness=${harness}"
    if [ -n "$version" ]; then
        manifest_params="${manifest_params}&version=${version}"
    fi

    local manifest
    manifest="$(_api_get "install/manifest?${manifest_params}")"

    local entry_count
    entry_count="$(echo "$manifest" | jq '.entries | length')"

    if [ "$entry_count" -eq 0 ]; then
        _die "No installable entries found for: $slug"
    fi

    # Detect if this is a group install (entries have non-null role)
    local is_group
    is_group="$(echo "$manifest" | jq '[.entries[] | select(.role != null)] | length > 0')"

    # Dry-run mode: print planned actions and exit
    if [ "$dry_run" = "true" ]; then
        _info "Dry run — the following would be installed:"
        echo "$manifest" | jq -r '.entries[] | [.role // "", .slug, .targetPath] | @tsv' | while IFS=$'\t' read -r role entry_slug target_path; do
            if [ -n "$role" ]; then
                printf '  [%s] %s -> %s\n' "$role" "$entry_slug" "$target_path"
            else
                printf '  %s -> %s\n' "$entry_slug" "$target_path"
            fi
        done
        exit 0
    fi

    # Step B: Download bundle
    local tmpdir
    tmpdir="$(mktemp -d)"
    # shellcheck disable=SC2064
    trap "rm -rf '$tmpdir'" EXIT

    local bundle_params="slugs=${slug}&harness=${harness}"
    if [ -n "$version" ]; then
        bundle_params="${bundle_params}&version=${version}"
    fi
    local bundle_path="${tmpdir}/bundle.zip"
    _api_get_binary "install/bundle?${bundle_params}" "$bundle_path"

    # Step C: Extract
    local extract_dir="${tmpdir}/extracted"
    _extract_zip "$bundle_path" "$extract_dir"

    # Fetch-only mode: copy extracted to dest and exit
    if [ "$fetch_only" = "true" ]; then
        mkdir -p "$dest"
        cp -r "${extract_dir}/"* "$dest/"
        _info "Extracted to: $dest"
        exit 0
    fi

    # Step D: Place each entry
    echo "$manifest" | jq -c '.entries[]' | while read -r entry_json; do
        local entry_slug entry_target entry_source entry_role
        entry_slug="$(echo "$entry_json" | jq -r '.slug')"
        entry_target="$(echo "$entry_json" | jq -r '.targetPath')"
        entry_source="$(echo "$entry_json" | jq -r '.sourcePath')"
        entry_role="$(echo "$entry_json" | jq -r '.role // empty')"

        # Expand ~ to HOME
        entry_target="${entry_target/#\~/$HOME}"

        local source_file="${extract_dir}/${entry_slug}/${entry_source}"

        # Print progress with role label if group install
        if [ -n "$entry_role" ]; then
            _info "[${entry_role}] Installing ${entry_slug} -> ${entry_target}"
        else
            _info "Installing ${entry_slug} -> ${entry_target}"
        fi

        # Handle JSON merge targets (paths with #)
        if [[ "$entry_target" == *"#"* ]]; then
            _json_merge "$source_file" "$entry_target"
        else
            # Standard file placement
            local target_dir
            target_dir="$(dirname "$entry_target")"
            mkdir -p "$target_dir"
            cp "$source_file" "$entry_target"
        fi
    done

    if [ "$is_group" = "true" ]; then
        _info "Group install complete: ${entry_count} member(s) installed."
    else
        _info "Install complete: ${slug}"
    fi
}

# === Pull Subcommand ===

_cmd_pull() {
    if [ $# -eq 0 ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
        cat <<'EOF'
Usage: agent-lib pull <slug> [--version VERSION]

Download raw artifact file (frontmatter + body).

Options:
  --version VERSION  Specific version (default: latest)
  --help, -h         Show this help

Examples:
  agent-lib pull my-skill
  agent-lib pull my-skill --version 1.2.0
EOF
        if [ $# -eq 0 ]; then exit 1; else exit 0; fi
    fi

    local slug="$1"; shift
    local version="latest"

    while [ $# -gt 0 ]; do
        case "$1" in
            --version) version="$2"; shift 2 ;;
            *) _die "Unknown option: $1" ;;
        esac
    done

    _require_server
    _check_prerequisites

    local response
    response="$(_api_get "artifacts/${slug}/${version}")"

    local filename
    filename="$(echo "$response" | jq -r '.primaryFileName // (.name + ".md")')"
    local content
    content="$(echo "$response" | jq -r '.rawContent')"

    printf '%s' "$content" > "$filename"
    _info "Downloaded: ${filename}"
}

# === Push Subcommand ===

_cmd_push() {
    if [ $# -eq 0 ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
        cat <<'EOF'
Usage: agent-lib push <file>

Upload a local artifact file to the library server.
File must contain valid YAML frontmatter.

Options:
  --help, -h    Show this help

Examples:
  agent-lib push ./my-agent.md
  agent-lib push ./skills/git-helper.md
EOF
        if [ $# -eq 0 ]; then exit 1; else exit 0; fi
    fi

    local file="$1"

    if [ ! -f "$file" ]; then
        _die "File not found: $file"
    fi

    _require_server
    _check_prerequisites

    local response
    response="$(_api_post_file "artifacts" "$file")"

    local slug version_str
    slug="$(echo "$response" | jq -r '.slug // .name // "unknown"')"
    version_str="$(echo "$response" | jq -r '.version // "unknown"')"

    _info "Uploaded ${slug} v${version_str}"
}

# === Main Dispatch ===

main() {
    if [ $# -eq 0 ]; then
        _usage
        exit 1
    fi

    case "$1" in
        --help|-h)
            _usage
            exit 0
            ;;
        --self-test)
            _self_test
            ;;
        install)
            shift
            _cmd_install "$@"
            ;;
        search)
            shift
            _cmd_search "$@"
            ;;
        list)
            shift
            _cmd_list "$@"
            ;;
        pull)
            shift
            _cmd_pull "$@"
            ;;
        push)
            shift
            _cmd_push "$@"
            ;;
        info)
            shift
            _cmd_info "$@"
            ;;
        *)
            _die "Unknown command: $1. Run 'agent-lib --help' for usage."
            ;;
    esac
}

main "$@"
