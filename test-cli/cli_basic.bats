#!/usr/bin/env bats
# cli_basic.bats — Basic CLI subcommand tests (search, list, info, pull)
# Requires: running server (see setup_suite.bash)

setup_suite() {
    source "${BATS_TEST_DIRNAME}/setup_suite.bash"
    wait_for_server
}

setup() {
    source "${BATS_TEST_DIRNAME}/setup_suite.bash"
}

# --- Search ---

@test "search returns seeded artifact" {
    run agent_lib search "agent-lib"
    [ "$status" -eq 0 ]
    [[ "$output" == *"agent-lib-sh"* ]]
}

@test "search with --type filter" {
    run agent_lib search "agent" --type skill
    [ "$status" -eq 0 ]
    # If results exist, verify all are skills (skip header lines)
    local data_lines
    data_lines=$(echo "$output" | tail -n +3)
    if [ -n "$data_lines" ]; then
        while IFS= read -r line; do
            [[ "$line" == *"skill"* ]] || [[ "$line" == "" ]]
        done <<< "$data_lines"
    fi
}

# --- List ---

@test "list returns artifacts" {
    run agent_lib list
    [ "$status" -eq 0 ]
    # Should have header + at least one data row
    local line_count
    line_count=$(echo "$output" | wc -l)
    [ "$line_count" -ge 3 ]
    # Header should contain SLUG and TYPE
    [[ "$output" == *"SLUG"* ]]
    [[ "$output" == *"TYPE"* ]]
}

@test "list with --type filter" {
    run agent_lib list --type skill
    [ "$status" -eq 0 ]
    # All data lines (after 2 header lines) should contain "skill"
    local data_lines
    data_lines=$(echo "$output" | tail -n +3)
    if [ -n "$data_lines" ]; then
        while IFS= read -r line; do
            [[ "$line" == *"skill"* ]] || [[ "$line" == "" ]]
        done <<< "$data_lines"
    fi
}

# --- Info ---

@test "info shows metadata" {
    run agent_lib info agent-lib-sh
    [ "$status" -eq 0 ]
    [[ "$output" == *"Name:"* ]]
    [[ "$output" == *"Type:"* ]]
    [[ "$output" == *"Version:"* ]]
}

# --- Pull ---

@test "pull downloads artifact file" {
    local tmpdir="${BATS_TMPDIR}/pull-test-$$"
    mkdir -p "$tmpdir"
    cd "$tmpdir"

    run agent_lib pull agent-lib-sh
    [ "$status" -eq 0 ]

    # Verify a file was created and is non-empty
    local found=false
    for f in "$tmpdir"/*; do
        if [ -f "$f" ] && [ -s "$f" ]; then
            found=true
            break
        fi
    done
    [ "$found" = "true" ]

    # Cleanup
    rm -rf "$tmpdir"
}
