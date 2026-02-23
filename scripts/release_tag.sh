#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

VERSION_NAME="$(extract_toml_value "versionName" "$VERSION_FILE")"
if [[ -z "$VERSION_NAME" ]]; then
  echo "ERROR: failed to parse versionName from $VERSION_FILE" >&2
  exit 2
fi

TAG_NAME="v$VERSION_NAME"

if ! git -C "$ROOT_DIR" diff --quiet || ! git -C "$ROOT_DIR" diff --cached --quiet; then
  echo "ERROR: working tree is not clean. Commit/stash changes before tagging." >&2
  exit 1
fi

if git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/$TAG_NAME" >/dev/null; then
  echo "ERROR: local tag already exists: $TAG_NAME" >&2
  exit 1
fi

if git -C "$ROOT_DIR" ls-remote --tags origin "refs/tags/$TAG_NAME" | grep -q .; then
  echo "ERROR: remote tag already exists: $TAG_NAME" >&2
  exit 1
fi

"$ROOT_DIR/scripts/check_release_guard.sh" "$TAG_NAME"

current_branch="$(git -C "$ROOT_DIR" branch --show-current)"
if [[ -z "$current_branch" ]]; then
  echo "ERROR: detached HEAD is not supported for release_tag.sh" >&2
  exit 1
fi

git -C "$ROOT_DIR" tag -a "$TAG_NAME" -m "$TAG_NAME"
git -C "$ROOT_DIR" push origin "$current_branch"
git -C "$ROOT_DIR" push origin "$TAG_NAME"

echo "Created and pushed tag: $TAG_NAME (branch: $current_branch)"
