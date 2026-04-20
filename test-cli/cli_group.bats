#!/usr/bin/env bats
# cli_group.bats — Group install tests (AC #5, #6)
# Requires: running server with a seeded agent-group

setup_suite() {
    source "${BATS_TEST_DIRNAME}/setup_suite.bash"
    wait_for_server
}

setup() {
    source "${BATS_TEST_DIRNAME}/setup_suite.bash"
}

# --- Group Install ---

@test "group install places all members at correct harness paths" {
    # Override HOME so ~ expansion resolves inside tmpdir
    local fake_home="${BATS_TMPDIR}/group-install-$$"
    mkdir -p "$fake_home"
    export HOME="$fake_home"

    # Use a working directory to capture relative-path installs (agent-claude type)
    local workdir="${BATS_TMPDIR}/group-workdir-$$"
    mkdir -p "$workdir"
    cd "$workdir"

    run agent_lib install dev-team-group --harness claude
    [ "$status" -eq 0 ]

    # Agent-group members are type agent-claude → .claude/agents/{name}.md (relative to CWD)
    # Expected members: manager, analyse, implementation
    [ -f "${workdir}/.claude/agents/manager.md" ]
    [ -f "${workdir}/.claude/agents/analyse.md" ]
    [ -f "${workdir}/.claude/agents/implementation.md" ]

    # Cleanup
    rm -rf "$fake_home" "$workdir"
}

@test "group dry-run lists all members without writing files" {
    local fake_home="${BATS_TMPDIR}/group-dryrun-$$"
    mkdir -p "$fake_home"
    export HOME="$fake_home"

    run agent_lib install dev-team-group --dry-run
    [ "$status" -eq 0 ]

    # Output should list ALL members (AND, not OR)
    [[ "$output" == *"manager"* ]]
    [[ "$output" == *"analyse"* ]]
    [[ "$output" == *"implementation"* ]]

    # No files should be written to disk
    local file_count
    file_count=$(find "$fake_home" -type f | wc -l)
    [ "$file_count" -eq 0 ]

    # Cleanup
    rm -rf "$fake_home"
}
