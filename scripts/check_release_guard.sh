#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAX_LEN="${PLAY_WHATSNEW_MAX:-500}"
TAG_NAME="${1:-}"
REQUIRED_LOCALES=(${PLAY_WHATSNEW_REQUIRED_LOCALES:-en-US zh-CN})

if [[ ! "$MAX_LEN" =~ ^[0-9]+$ ]]; then
  echo "ERROR: PLAY_WHATSNEW_MAX must be an integer, got '$MAX_LEN'." >&2
  exit 2
fi

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
if [[ ! -f "$VERSION_FILE" ]]; then
  echo "ERROR: Missing $VERSION_FILE" >&2
  exit 2
fi

VERSION_NAME="$(extract_toml_value "versionName" "$VERSION_FILE")"
VERSION_CODE="$(extract_toml_value "versionCode" "$VERSION_FILE")"

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
  echo "ERROR: Failed to parse versionName/versionCode from $VERSION_FILE" >&2
  exit 2
fi

echo "Release guard"
echo "- versionName: $VERSION_NAME"
echo "- versionCode: $VERSION_CODE"
echo "- max whatsnew length: $MAX_LEN"
echo "- required locales: ${REQUIRED_LOCALES[*]}"

FAIL=0

WHATSNEW_DIR="$ROOT_DIR/distribution/whatsnew"
if [[ ! -d "$WHATSNEW_DIR" ]]; then
  echo "ERROR: Missing $WHATSNEW_DIR" >&2
  exit 2
fi

shopt -s nullglob
files=("$WHATSNEW_DIR"/whatsnew-*)
shopt -u nullglob

if (( ${#files[@]} == 0 )); then
  echo "ERROR: No whatsnew files found in $WHATSNEW_DIR" >&2
  exit 2
fi

for file in "${files[@]}"; do
  locale="$(basename "$file")"
  locale="${locale#whatsnew-}"
  count="$(wc -m < "$file" | tr -d '[:space:]')"
  if (( count == 0 )); then
    echo "FAIL: $locale is empty ($file)"
    FAIL=1
  elif (( count > MAX_LEN )); then
    echo "FAIL: $locale length=$count exceeds max=$MAX_LEN ($file)"
    FAIL=1
  else
    echo "PASS: $locale length=$count/$MAX_LEN"
  fi

done

for locale in "${REQUIRED_LOCALES[@]}"; do
  file="$WHATSNEW_DIR/whatsnew-$locale"
  if [[ ! -f "$file" ]]; then
    echo "FAIL: required whatsnew locale missing: $locale ($file)"
    FAIL=1
  fi
done

CHANGELOG_FILE="$ROOT_DIR/docs/CHANGELOG.md"
if [[ ! -f "$CHANGELOG_FILE" ]]; then
  echo "FAIL: missing changelog file ($CHANGELOG_FILE)"
  FAIL=1
elif grep -Fq "## [v$VERSION_NAME]" "$CHANGELOG_FILE"; then
  echo "PASS: changelog contains section for v$VERSION_NAME"
else
  echo "FAIL: changelog section not found: ## [v$VERSION_NAME] in $CHANGELOG_FILE"
  FAIL=1
fi

if [[ -n "$TAG_NAME" ]]; then
  expected_tag="v$VERSION_NAME"
  if [[ "$TAG_NAME" != "$expected_tag" ]]; then
    echo "FAIL: tag mismatch. got '$TAG_NAME', expected '$expected_tag' from versionName."
    FAIL=1
  else
    echo "PASS: tag matches versionName ($TAG_NAME)"
  fi
fi

if (( FAIL != 0 )); then
  echo "Release guard failed." >&2
  exit 1
fi

echo "Release guard passed."
