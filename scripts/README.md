# Scripts

This directory contains XposedSmsCode-owned release and maintenance automation.

- `config/`: task-selection, compatibility, dual-track, and release-policy configuration.
- `release/`: project-specific release metadata and tag checks.

Shared task selection, dual-track analysis, compatibility execution, and release
primitives live in `magisk-ci-toolkit`; consumer copies are intentionally not kept.
Use one full 40-character toolkit commit SHA across GitLab includes, the resolver,
and GitHub action variables.
