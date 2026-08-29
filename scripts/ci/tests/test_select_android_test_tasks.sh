#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
selector="$(cd "$script_dir/../../.." && pwd)/scripts/ci/select_android_test_tasks.sh"
tmp_dir="$(mktemp -d)"; trap 'rm -rf "$tmp_dir"' EXIT
assert_contains() { grep -Fx -- "$1" "$2" >/dev/null || { echo "missing $1" >&2; exit 1; }; }
printf 'impact\napp/src/main/App.kt\n' > "$tmp_dir/app"
bash "$selector" /dev/null "$tmp_dir/app" > "$tmp_dir/out"; assert_contains ':app:check' "$tmp_dir/out"; assert_contains ':app:koverVerifyGithubDebug' "$tmp_dir/out"
printf 'impact\nsmscode/core/domain/src/main.kt\n' > "$tmp_dir/core"
bash "$selector" /dev/null "$tmp_dir/core" > "$tmp_dir/out"; assert_contains ':smscode-core:domain:test' "$tmp_dir/out"
printf 'impact\ndocs/ci.md\n' > "$tmp_dir/docs"
bash "$selector" /dev/null "$tmp_dir/docs" > "$tmp_dir/out"; [[ ! -s "$tmp_dir/out" ]] || exit 1
printf 'full\n' > "$tmp_dir/full"
bash "$selector" /dev/null "$tmp_dir/full" > "$tmp_dir/out"; assert_contains ':runtime:check' "$tmp_dir/out"
echo 'XposedSmsCode selector tests passed'
