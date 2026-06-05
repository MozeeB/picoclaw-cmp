# Contributing to PicoClaw CMP

Thanks for your interest in contributing! 🎉 This document explains how to get set up and the
conventions we follow.

## Code of Conduct

This project adheres to a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are
expected to uphold it. Please report unacceptable behavior via a GitHub issue.

## Getting started

1. **Fork** the repo and clone your fork.
2. Create a `local.properties` with your Android SDK path:
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   ```
3. Verify the build before making changes:
   ```bash
   ./gradlew :shared:jvmTest
   ```

## Development workflow

This project follows the pipeline documented in [CLAUDE.md](CLAUDE.md). In short:

1. **Research first** — check existing code and official docs before adding dependencies.
2. **Plan** — for anything touching more than ~2 files, sketch the approach first.
3. **TDD** — write the failing test, make it pass, refactor. Aim for ≥ 80 % coverage on
   `commonMain`; the MVI core (`ServiceState`, `ServiceIntent`, `ServiceViewModel`) should stay at 100 %.
4. **Verify all targets compile** before opening a PR:
   ```bash
   ./gradlew :shared:compileKotlinJvm :shared:compileKotlinIosArm64 \
     :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileAndroidMain
   ```
5. **Run tests:**
   ```bash
   ./gradlew :shared:jvmTest :shared:testAndroidHostTest
   ```

## Coding conventions

- **Immutability** — never mutate state; use `data class` `copy()`. State flows through `ServiceIntent` only.
- **MVI** — UI dispatches intents; the ViewModel is the single place state changes.
- **`expect`/`actual`** — all platform-divergent code; never `if (Platform.isAndroid)` in `commonMain`.
- **No third-party** for things we implement in-house (QR encoding, binary download) — see CLAUDE.md.
- **Latest stable deps only** — no alpha/beta/rc; verify Kotlin 2.3.21 / K2 compatibility.
- **File size** — 800 lines max; one `@Composable` screen per file.
- **No hardcoded** colors, strings, or sizes in composables — use `MaterialTheme.*` and `UiConstants`.

## Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add system tray support on desktop
fix: correct public-mode IP detection on Android
refactor: extract AdaptiveNavBar into shared widget
docs: update README build steps
test: add ServiceViewModel download tests
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`.

## Pull requests

- Keep PRs focused — one logical change per PR.
- Describe **what** changed and **why**, and include a test plan.
- Ensure all targets compile and tests pass.
- Link any related issues (`Closes #123`).

## Reporting bugs / requesting features

Open an issue using the provided templates. Include platform, OS version, steps to reproduce,
and logs where relevant.

Thank you for helping make PicoClaw CMP better! 💙
