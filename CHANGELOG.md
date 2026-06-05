# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial Kotlin/Compose Multiplatform port of PicoClaw UI (Android, iOS, Desktop, Web).
- MVI architecture: `ServiceState`, `ServiceIntent`, `ServiceViewModel`.
- Koin dependency injection across all platforms.
- AndroidX DataStore settings persistence (in-memory fallback on Web).
- Dashboard, WebView, Logs, and Config screens.
- Adaptive navigation (bottom bar / side rail).
- 6 themes (Carbon, Slate, Obsidian, Ebony, Nord, Sakura) and 12 locales.
- In-house pure-Kotlin QR code encoder + Compose renderer (no third-party library).
- `CoreServiceAdapter` with binary validation and crash-safe service start.
- Runtime binary downloader from GitHub releases (Desktop + Android), with Browse / Validate / Download UI.
- Public mode: binds `0.0.0.0`, adds `-public`, auto-detects LAN IP for a scannable QR/URL, and persists.

[Unreleased]: https://github.com/MozeeB/picoclaw-cmp/commits/main
