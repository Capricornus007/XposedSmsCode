#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

bash scripts/with_workspace_gradle_lock.sh \
  :smscode-core:smscode-domain:testDebugUnitTest \
  :magisk-ui-kit:validateDebugScreenshotTest \
  :core:testGithubApi101DebugUnitTest \
  :core:compileGithubApi101DebugKotlin \
  :app:check
