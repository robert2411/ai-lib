---
name: agent-lib-common-sh
type: skill
version: 1.0.0
description: Agent Library CLI shared helper functions (agent-lib-common.sh)
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
# agent-lib-common.sh — Shared helper functions for agent-lib CLI
# Requires: curl, jq; optional: unzip or python3 (for bundle extraction)

# === Output Helpers ===

_die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

_info() {
    printf '%s\n' "$*" >&2
}

_warn() {
    printf 'WARNING: %s\n' "$*" >&2
}

# === Prerequisite Checks ===

_require_cmd() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        _die "Required command not found: $cmd"
    fi
}

_check_prerequisites() {
    _require_cmd curl
    _require_cmd jq
    # unzip is optional — checked at extraction time with python3 fallback
}

# === Environment ===

_require_server() {
    if [ -z "${AGENT_LIB_SERVER:-}" ]; then
        _die "AGENT_LIB_SERVER environment variable is not set. Set it to your library server URL."
    fi
}

_auth_header() {
    if [ -n "${AGENT_LIB_USER:-}" ] && [ -n "${AGENT_LIB_PASS:-}" ]; then
        printf '%s' "-u ${AGENT_LIB_USER}:${AGENT_LIB_PASS}"
    fi
}

# === HTTP Helpers ===

_api_get() {
    local path="$1"
    local url="${AGENT_LIB_SERVER}/api/v1/${path}"
    local auth_args=""
    if [ -n "${AGENT_LIB_USER:-}" ] && [ -n "${AGENT_LIB_PASS:-}" ]; then
        auth_args="-u ${AGENT_LIB_USER}:${AGENT_LIB_PASS}"
    fi
    # shellcheck disable=SC2086
    curl -sSf $auth_args "$url"
}

_api_get_binary() {
    local path="$1"
    local output="$2"
    local url="${AGENT_LIB_SERVER}/api/v1/${path}"
    local auth_args=""
    if [ -n "${AGENT_LIB_USER:-}" ] && [ -n "${AGENT_LIB_PASS:-}" ]; then
        auth_args="-u ${AGENT_LIB_USER}:${AGENT_LIB_PASS}"
    fi
    # shellcheck disable=SC2086
    curl -sSf $auth_args -o "$output" "$url"
}

_api_post() {
    local path="$1"
    local body="$2"
    local content_type="${3:-application/json}"
    local url="${AGENT_LIB_SERVER}/api/v1/${path}"
    local auth_args=""
    if [ -n "${AGENT_LIB_USER:-}" ] && [ -n "${AGENT_LIB_PASS:-}" ]; then
        auth_args="-u ${AGENT_LIB_USER}:${AGENT_LIB_PASS}"
    fi
    # shellcheck disable=SC2086
    curl -sSf $auth_args -X POST -H "Content-Type: ${content_type}" -d "$body" "$url"
}

_api_post_file() {
    local path="$1"
    local file="$2"
    local url="${AGENT_LIB_SERVER}/api/v1/${path}"
    local auth_args=""
    if [ -n "${AGENT_LIB_USER:-}" ] && [ -n "${AGENT_LIB_PASS:-}" ]; then
        auth_args="-u ${AGENT_LIB_USER}:${AGENT_LIB_PASS}"
    fi
    # shellcheck disable=SC2086
    curl -sSf $auth_args -X POST -H "Content-Type: text/plain" --data-binary @"$file" "$url"
}

# === Harness Resolution ===

_resolve_harness() {
    local harness="${1:-}"
    if [ -n "$harness" ]; then
        printf '%s' "$harness"
        return
    fi
    # Default to claude if not specified
    printf '%s' "claude"
}

# === Zip Extraction ===

_extract_zip() {
    local zipfile="$1"
    local destdir="$2"
    mkdir -p "$destdir"
    if command -v unzip >/dev/null 2>&1; then
        unzip -o "$zipfile" -d "$destdir" >/dev/null 2>&1
    elif command -v python3 >/dev/null 2>&1; then
        python3 -m zipfile -e "$zipfile" "$destdir"
    else
        _die "Neither unzip nor python3 available for archive extraction"
    fi
}

# === JSON Merge Helper ===

_json_merge() {
    local src_file="$1"
    local target_path="$2"

    local file_path="${target_path%%#*}"
    local key_path="${target_path##*#}"

    # Expand ~ to HOME
    file_path="${file_path/#\~/$HOME}"

    local parent_dir
    parent_dir="$(dirname "$file_path")"
    mkdir -p "$parent_dir"

    # Build jq path array from dotted key (e.g. "mcpServers.my-server" -> ["mcpServers","my-server"])
    local jq_path_array="["
    local first=true
    local IFS_SAVE="$IFS"
    IFS='.'
    # shellcheck disable=SC2086
    set -- $key_path
    IFS="$IFS_SAVE"
    for segment in "$@"; do
        if [ "$first" = "true" ]; then
            jq_path_array="${jq_path_array}\"${segment}\""
            first=false
        else
            jq_path_array="${jq_path_array},\"${segment}\""
        fi
    done
    jq_path_array="${jq_path_array}]"

    if [ ! -f "$file_path" ]; then
        # Create new file with nested structure
        jq -n --argjson val "$(cat "$src_file")" --argjson path "$jq_path_array" \
            'setpath($path; $val)' > "$file_path"
    else
        # Merge into existing file at the nested path
        local tmp_file="${file_path}.tmp"
        jq --argjson val "$(cat "$src_file")" --argjson path "$jq_path_array" \
            'setpath($path; (getpath($path) // {}) + $val)' "$file_path" > "$tmp_file"
        # Validate result
        if jq . "$tmp_file" >/dev/null 2>&1; then
            mv "$tmp_file" "$file_path"
        else
            rm -f "$tmp_file"
            _die "JSON merge produced invalid output for: $file_path"
        fi
    fi
}

# === Formatting ===

_truncate() {
    local str="$1"
    local max="${2:-60}"
    if [ "${#str}" -gt "$max" ]; then
        printf '%s...' "${str:0:$((max - 3))}"
    else
        printf '%s' "$str"
    fi
}

_print_table_header() {
    printf '%-30s %-15s %s\n' "SLUG" "TYPE" "$1"
    printf '%-30s %-15s %s\n' "----" "----" "$(printf '%0.s-' $(seq 1 ${#1}))"
}
