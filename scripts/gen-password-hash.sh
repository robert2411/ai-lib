#!/usr/bin/env bash
# gen-password-hash.sh — Generate a BCrypt password hash for users.yaml
#
# Usage:
#   ./scripts/gen-password-hash.sh <password>
#   ./scripts/gen-password-hash.sh              (prompts interactively)
#
# The output is a BCrypt hash suitable for pasting into data/users.yaml.
# Uses ./mvnw exec:java to avoid Spring Boot fat JAR classpath issues.

set -euo pipefail

# Resolve project root (script lives in scripts/)
cd "$(dirname "$0")/.."

PASSWORD="${1:-}"

if [ -z "$PASSWORD" ]; then
    printf "Enter password: " >&2
    read -s PASSWORD
    echo >&2
    if [ -z "$PASSWORD" ]; then
        echo "Error: password must not be empty" >&2
        exit 1
    fi
fi

./mvnw -q exec:java \
    -Dexec.mainClass=com.agentlibrary.cli.BcryptCli \
    -Dexec.args="$PASSWORD" \
    2>/dev/null
