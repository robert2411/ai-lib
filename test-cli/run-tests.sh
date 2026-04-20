#!/usr/bin/env bash
# run-tests.sh — Convenience wrapper to run bats CLI integration tests
#
# Usage:
#   ./test-cli/run-tests.sh [--no-teardown]
#
# CI usage:
#   In CI pipelines, docker compose is started beforehand by the pipeline.
#   This script simply runs bats against the test-cli/*.bats files.
#   Ensure bats-core is installed in the CI environment.
#
# Prerequisites:
#   - bats-core installed (brew install bats-core / apt-get install bats)
#   - docker compose (for local dev; CI handles this separately)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

NO_TEARDOWN=false
if [ "${1:-}" = "--no-teardown" ]; then
    NO_TEARDOWN=true
fi

# Check for bats
if ! command -v bats >/dev/null 2>&1; then
    echo "SKIP: bats-core not installed. Install with: brew install bats-core" >&2
    echo "Tests skipped (bats unavailable)."
    exit 0
fi

# Export server config (default to localhost)
export AGENT_LIB_SERVER="${AGENT_LIB_SERVER:-http://localhost:8080}"
export AGENT_LIB_USER="${AGENT_LIB_USER:-admin}"
export AGENT_LIB_PASS="${AGENT_LIB_PASS:-changeme}"

# Start docker compose if server is not already running
STARTED_COMPOSE=false
if ! curl -sSf "${AGENT_LIB_SERVER}/actuator/health" >/dev/null 2>&1; then
    echo "Starting docker compose..."
    cd "$PROJECT_ROOT"
    docker compose up -d
    STARTED_COMPOSE=true

    # Wait for health (up to 60 seconds)
    echo "Waiting for server health..."
    elapsed=0
    while [ "$elapsed" -lt 60 ]; do
        if curl -sSf "${AGENT_LIB_SERVER}/actuator/health" >/dev/null 2>&1; then
            echo "Server is healthy."
            break
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    if [ "$elapsed" -ge 60 ]; then
        echo "ERROR: Server did not become healthy within 60s" >&2
        docker compose logs --tail=50
        docker compose down
        exit 1
    fi
fi

# Run bats tests
echo "Running bats tests..."
EXIT_CODE=0
bats "$SCRIPT_DIR"/*.bats || EXIT_CODE=$?

# Teardown docker compose if we started it and --no-teardown not set
if [ "$STARTED_COMPOSE" = "true" ] && [ "$NO_TEARDOWN" = "false" ]; then
    echo "Tearing down docker compose..."
    cd "$PROJECT_ROOT"
    docker compose down
fi

if [ "$EXIT_CODE" -eq 0 ]; then
    echo "All CLI tests passed."
else
    echo "CLI tests failed with exit code: $EXIT_CODE" >&2
fi

exit "$EXIT_CODE"
