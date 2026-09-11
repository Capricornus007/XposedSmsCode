# Build Instructions

This project depends on private GitLab submodules. To build, you need:

## Required Secrets (GitHub Actions)
- `GITLAB_TOKEN`: A GitLab personal access token with `read_repository` scope.
  This is used to clone the private submodules from `gitlab.com/magisk3171/shared/`.

## Local Build

```bash
# Configure git to use your GitLab token
git config --global url."https://oauth2:YOUR_TOKEN@gitlab.com/".insteadOf "https://gitlab.com/"

# Initialize submodules
git submodule update --init --recursive

# Build
./gradlew :app:assembleRelease
```

## Submodule Dependencies
- `smscode/core` - Core implementation modules (hook, domain, runtime, contract, rule, verification)
- `magisk-ui-kit` - UI components
- `magisk-xposed-kit` - Xposed framework utilities
- `build-logic` - Custom Gradle build plugins
- `smscode/rules` - SMS code matching rules