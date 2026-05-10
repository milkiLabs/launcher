# Milki Launcher

A custom Android Launcher app built from scratch with kotlin and compose.

## Features

- **Multi-Mode Search**: Search apps, contacts, web, and YouTube
  - No prefix: Search installed apps
  - `s `: Web search
  - `c `: Contacts search
  - `y `: YouTube search

- **Smart Layout**: Grid for apps, list for provider results
- **Recent Apps**: Saves 8 most recently used apps
- **Material Design 3**: Modern UI with dynamic colors support
- **Performance Optimized**: O(n) search, controlled parallelism, memory caching
## Documentation

Comprehensive documentation is available in the [docs/](docs/) directory:

- **[Documentation Index](docs/README.md)** - Entry point for all project docs
- **[Architecture](docs/Architecture.md)** - Current package map, layer boundaries, and runtime flow
- **[Conventions](docs/Conventions.md)** - Placement rules, naming standards, and engineering guardrails
- **[Contributing](docs/Contributing.md)** - New-contributor setup, workflow, and review checklist
- **[Performance](docs/Performance.md)** - Benchmarking, baseline profiles, and real-device profiling workflow

## CI/CD (Tag-Based APK Releases)

This repository includes a GitHub Actions workflow at `.github/workflows/android-release.yml`.

- It does **not** run on every commit.
- It runs only when you push a tag like `v1.0.0` or `release-2026-04-15`.
- It builds and uploads a signed `release` APK artifact.
- For tag builds, generated APKs are attached to a GitHub Release automatically.

### Required GitHub Secrets (for signed release APK)

Add these repository secrets in GitHub:

- `ANDROID_KEYSTORE_BASE64` (base64 content of your `.jks` file)
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

If these secrets are not set, the workflow fails because release signing is required.

### Create and Push a Release Tag

```bash
git tag v1.0.0
git push origin v1.0.0
```
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
 base64 -w 0 release-keystore.jks | wl-copy