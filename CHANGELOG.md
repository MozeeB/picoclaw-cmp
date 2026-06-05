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
- **Desktop system tray** (Compose `Tray`): show/hide window, start/stop service, quit; minimize-to-tray on window close — tray and UI share one `ServiceViewModel`.
- **Desktop window-state persistence**: window size & position are saved and restored across launches.
- **Android auto-start on boot**: `BootReceiver` starts the foreground service after reboot when "Auto-start on launch" is enabled.
- **Localization**: in-house type-safe i18n covering all 12 locales (en, zh, es, fr, de, ru, pt, ja, ko, id, ar, hi); switching language in Config updates the whole UI instantly.
- **Analytics**: `Analytics` abstraction with a no-op default and an `AndroidAnalytics` stub (Firebase-ready), gated by the Telemetry toggle and persisted.
- **iOS WebView**: native `WKWebView` embedded via `UIKitView`.

### Fixed
- **Android WebView `ERR_CLEARTEXT_NOT_PERMITTED`**: added a network-security-config permitting
  cleartext to loopback, and the embedded WebView now loads the loopback URL (`127.0.0.1`) — the
  local service is always reachable there, even in public mode. The LAN IP is still used for the QR/URL.
- **WebView `ERR_CONNECTION_REFUSED`**: the binary is now launched as `<binary> -port <port> [args]`
  (matching picoclaw_fui) instead of with made-up `--host`/`--path` flags that prevented it from
  binding the port. The embedded Android WebView also auto-retries the initial load to cover the
  brief window between process start and the server binding the port.
- **WebView still refused (`unknown command "18800"`)**: the release ships two binaries — `picoclaw`
  (agent/gateway CLI, which can't serve the web UI) and `picoclaw-launcher` (which does). The
  downloader now extracts **both** and binary resolution **prefers `picoclaw-launcher`**, so the
  web console actually starts. Re-download the binary (or point Config at `picoclaw-launcher`) to apply.
- **Device IP**: public mode no longer reports a link-local APIPA address (`169.254.x.x`); it now
  prefers a real private LAN IP.

[Unreleased]: https://github.com/MozeeB/picoclaw-cmp/commits/main
