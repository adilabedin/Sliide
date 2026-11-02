#!/usr/bin/env bash
# Stop hook: lint gate that runs at the end of every agent turn.
#
# If Kotlin files changed since the last clean lint, run `./gradlew ktlintCheck`.
# On new violations, exit 2: Claude Code feeds stderr back to the agent and the
# turn continues so it can fix them. There are no baselines: every module must be clean.
# Only reports written by this run's check tasks are shown, never stale ones from earlier runs.
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 0
input=$(cat)

# Kotlin files that differ from HEAD (or are untracked). Nothing changed → nothing to lint.
changed=$( { git diff --name-only HEAD -- '*.kt' '*.kts' 2>/dev/null || git diff --name-only --cached -- '*.kt' '*.kts'
             git diff --name-only -- '*.kt' '*.kts'
             git ls-files --others --exclude-standard -- '*.kt' '*.kts'; } | sort -u)
[ -z "$changed" ] && exit 0

# Skip if these exact contents already passed (so a turn with no new edits stays fast).
state_dir=.claude/.cache; mkdir -p "$state_dir"
fingerprint=$(for f in $changed; do [ -f "$f" ] && { echo "$f"; cat "$f"; }; done | shasum | cut -d' ' -f1)
[ "$(cat "$state_dir/lint-ok" 2>/dev/null)" = "$fingerprint" ] && exit 0

# Marker for "reports written by this run". Older reports under build/ (from earlier runs, deleted or renamed files,
# or ktlintFormat, which lists what it already fixed) must not be reported as new violations.
run_marker="$state_dir/lint-run"
touch "$run_marker"

if output=$(./gradlew ktlintCheck --console=plain -q 2>&1); then
  echo "$fingerprint" > "$state_dir/lint-ok"
  exit 0
fi

# A failing check task never counts as up to date, so its report is always rewritten by this run.
violations=$(find . -path '*/build/reports/ktlint/*' -path '*Check*' -name '*.txt' -size +0 -newer "$run_marker" \
               -exec cat {} + 2>/dev/null | sed $'s/\x1b\\[[0-9;]*m//g')
report="ktlint found new violations:
${violations:-$output}

Fix them (\`./gradlew ktlintFormat\` handles most), then re-run \`./gradlew ktlintCheck\`.
Do NOT add them to ktlint-baseline.xml."

# Already in a turn forced by this hook: warn instead of blocking again, so an
# unfixable violation can't trap the agent in a loop.
if [ "$(echo "$input" | jq -r '.stop_hook_active // false')" = "true" ]; then
  jq -n --arg m "Lint gate: violations remain after one fix attempt. Run ./gradlew ktlintCheck." '{systemMessage: $m}'
  exit 0
fi

echo "$report" >&2
exit 2
