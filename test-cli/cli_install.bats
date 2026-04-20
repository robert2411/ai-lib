#!/usr/bin/env bats
# cli_install.bats — Install subcommand tests
# Requires: running server (see setup_suite.bash)

setup_suite() {
    source "${BATS_TEST_DIRNAME}/setup_suite.bash"
    wait_for_server
}

setup() {
    source "${BATS_TEST_DIRNAME}/setup_suite.bash"
}

# --- Install ---

@test "install places file at harness path" {
    # Override HOME so install writes inside tmpdir
    local fake_home="${BATS_TMPDIR}/install-home-$$"
    mkdir -p "$fake_home"
    export HOME="$fake_home"

    run agent_lib install agent-lib-sh --harness claude
    [ "$status" -eq 0 ]

    # Verify file exists at exact expected harness path for claude/skill:
    # ~/.claude/skills/agent-lib-sh/skill.md
    local expected_path="${fake_home}/.claude/skills/agent-lib-sh/skill.md"
    [ -f "$expected_path" ]
    [ -s "$expected_path" ]

    # Cleanup
    rm -rf "$fake_home"
}

@test "install --fetch-only extracts to dest directory" {
    local dest="${BATS_TMPDIR}/fetch-test-$$"
    mkdir -p "$dest"

    run agent_lib install agent-lib-sh --fetch-only --dest "$dest"
    [ "$status" -eq 0 ]

    # Verify directory has content
    local file_count
    file_count=$(find "$dest" -type f | wc -l)
    [ "$file_count" -gt 0 ]

    # Cleanup
    rm -rf "$dest"
}
