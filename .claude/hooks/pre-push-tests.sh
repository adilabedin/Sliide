#!/usr/bin/env bash
# PreToolUse(Bash) hook: test gate in front of `git push`.
#
# When the agent runs a Bash command containing `git push`, run the unit tests first.
# If they fail, exit 2: Claude Code blocks the push and feeds the failures back to the
# agent. Any other command passes straight through.
set -uo pipefail

command=$(jq -r '.tool_input.command // empty')

# Match `git push` anywhere in the command (also after `&&`, `;`, `cd x &&`, or `git -C dir push`).
echo "$command" | grep -Eq '(^|[^[:alnum:]_-])git([[:space:]]+-[^[:space:]]+([[:space:]]+[^-[:space:]][^[:space:]]*)?)*[[:space:]]+push([[:space:]]|$)' || exit 0

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 0

if output=$(./gradlew testAndroidHostTest --console=plain 2>&1); then
  exit 0
fi

{
  echo "Push blocked: unit tests failed (./gradlew testAndroidHostTest)."
  echo
  echo "$output" | grep -E 'FAILED|Test.*>|Exception|Error|expected|BUILD' | head -60
  echo
  echo "Fix the failing tests or the code under test, then push again."
  echo "Do NOT skip, disable or weaken tests to get the push through."
} >&2
exit 2
