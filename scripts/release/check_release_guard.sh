#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
export MAGISK_RELEASE_POLICY_FILE="$ROOT_DIR/scripts/config/release-policy.json"

# Accept both "<tag>" (CI invocation) and "<root-dir> <tag>" (toolkit entry
# point convention).
if [[ -n "${1:-}" ]] && [[ -d "$1" ]]; then
  TAG_NAME="${2:-}"
else
  TAG_NAME="${1:-}"
fi

# The base checks, the Play metadata checks and the project_checks dispatch all
# live in the toolkit; scripts/config/release-policy.json drives what runs.
# shellcheck source=check_release_guard.sh
source "${TOOLKIT_DIR}/release/check_release_guard.sh"
check_release_guard "$ROOT_DIR" "$TAG_NAME"
