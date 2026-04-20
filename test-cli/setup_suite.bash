#!/usr/bin/env bash
# setup_suite.bash — bats helper for agent-lib CLI integration tests
# Sources by bats automatically when placed alongside *.bats files.

# Server configuration (override via environment)
export AGENT_LIB_SERVER="${AGENT_LIB_SERVER:-http://localhost:8080}"
export AGENT_LIB_USER="${AGENT_LIB_USER:-admin}"
export AGENT_LIB_PASS="${AGENT_LIB_PASS:-changeme}"

# Script directory (points to ../scripts/)
export SCRIPT_DIR="${BATS_TEST_DIRNAME}/../scripts"

# Helper: invoke the CLI
agent_lib() {
    bash "${SCRIPT_DIR}/agent-lib.sh" "$@"
}

# Helper: wait for server health (poll up to 30s)
wait_for_server() {
    local max_wait="${1:-30}"
    local interval=2
    local elapsed=0

    while [ "$elapsed" -lt "$max_wait" ]; do
        if curl -sSf "${AGENT_LIB_SERVER}/actuator/health" >/dev/null 2>&1; then
            return 0
        fi
        sleep "$interval"
        elapsed=$((elapsed + interval))
    done

    echo "ERROR: Server not reachable at ${AGENT_LIB_SERVER} after ${max_wait}s" >&2
    return 1
}
