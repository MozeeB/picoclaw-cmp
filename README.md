<div align="center">

# PicoClaw CMP

**A cross-platform management frontend for the [PicoClaw](https://github.com/sipeed/picoclaw) service — built with Kotlin & Compose Multiplatform.**

Runs natively on **Android · iOS · Desktop (macOS/Windows/Linux) · Web (Wasm/JS)** from a single Kotlin codebase.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.11-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Platforms](https://img.shields.io/badge/platforms-Android%20·%20iOS%20·%20Desktop%20·%20Web-informational)](#-platform-support)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## ✨ Overview

PicoClaw CMP is the **Kotlin/Compose Multiplatform** rewrite of the Flutter-based PicoClaw UI. It manages the
PicoClaw service — a local network reverse proxy / tunnelling daemon — with a single, native UI across every platform.

### Four screens

| Tab | What it does |
|-----|--------------|
| **Dashboard** | Service status, start/stop, public-mode toggle, live URL + scannable QR code |
| **WebView** | Embedded view of the PicoClaw local web UI |
| **Logs** | Real-time log viewer with filter, auto-scroll, and export |
| **Config** | Host/port/path, binary management (browse/validate/**download**), 6 themes, 12 languages, autostart |

---

## 🚀 Features

- **MVI architecture** — unidirectional state flow, fully immutable state, 100 % testable core
- **Runtime binary downloader** — fetches the PicoClaw core from GitHub releases and installs it (Desktop + Android), no manual setup
- **In-house QR encoder** — pure-Kotlin QR code generation (ISO/IEC 18004), zero third-party libraries
- **6 themes** — Carbon, Slate, Obsidian, Ebony, Nord, Sakura
- **12 languages** — en, zh, es, fr, de, ru, pt, ja, ko, id, ar, hi
- **Public mode** — exposes the service on your LAN with an auto-detected, scannable IP
- **Adaptive layout** — bottom navigation on phones, side rail on tablets/desktop

---

## 📦 Platform support

| Feature | Android | Desktop (JVM) | iOS | Web |
|---------|:-------:|:-------------:|:---:|:---:|
| Service start/stop | ✅ foreground service | ✅ process spawn | — stub | — stub |
| Binary download | ✅ | ✅ | — | — |
| WebView | ✅ native | ✅ system browser | ⏳ Phase 4 | ⏳ Phase 4 |
| Settings persistence | ✅ DataStore | ✅ DataStore | ✅ DataStore | in-memory |
| QR code | ✅ | ✅ | ✅ | ✅ |

---

## 🛠 Tech stack

| Concern | Choice |
|---------|--------|
| UI | Compose Multiplatform 1.11 |
| Language | Kotlin 2.3.21 (K2) |
| DI | Koin 4.1 |
| State | MVI + `StateFlow` + AndroidX Lifecycle ViewModel |
| Persistence | AndroidX DataStore (official KMP) |
| Serialization | kotlinx.serialization |
| Networking | `java.net.HttpURLConnection` (no third-party HTTP lib) |

---

## 🏃 Getting started

### Prerequisites
- JDK 17+
- Android SDK (for the Android target)
- Xcode (for the iOS target, macOS only)

### Clone

```bash
git clone https://github.com/MozeeB/picoclaw-cmp.git
cd picoclaw-cmp
```

> Create a `local.properties` with your Android SDK path (this file is **not** committed):
> ```properties
> sdk.dir=/path/to/your/Android/sdk
> ```

### Run

```bash
# Android
./gradlew :androidApp:installDebug

# Desktop (JVM)
./gradlew :desktopApp:run

# Web (WasmJS — recommended)
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# Web (JS)
./gradlew :webApp:jsBrowserDevelopmentRun

# iOS — open ./iosApp in Xcode and run
```

### Test

```bash
./gradlew :shared:allTests            # all platforms
./gradlew :shared:jvmTest             # JVM only (fastest)
./gradlew :shared:testAndroidHostTest # Android unit tests
```

---

## 🏗 Architecture

```
View (@Composable)
  │  dispatches ServiceIntent
  ▼
ServiceViewModel  ──►  StateFlow<ServiceState>  (immutable)
  │  processes intent → new state              │  observed by View
  ▼                                            ▼
CoreServiceAdapter (expect/actual)        View recomposes
```

- **Model** — `ServiceState` (immutable `data class`)
- **Intent** — `ServiceIntent` (sealed interface)
- **ViewModel** — `ServiceViewModel.onIntent()` emits a new state for every change
- **Adapters** — platform `expect/actual` for service control, settings, file pick, binary download

See [CLAUDE.md](CLAUDE.md) for the full architecture, conventions, and contributor playbook.

```
shared/src/commonMain/kotlin/com/mozeeb/picoclaw/cmp/
├── mvi/        ServiceState · ServiceIntent · ServiceViewModel
├── core/       CoreServiceAdapter · BinaryDownloader · SettingsRepository · AppSettings
├── di/         Koin modules (AppModule + per-platform PlatformModule)
└── ui/         App · AppTheme · pages/ · widgets/ (incl. in-house QrCodeCanvas)
```

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) and our
[Code of Conduct](CODE_OF_CONDUCT.md) before opening a PR.

---

## 📄 License

Licensed under the [MIT License](LICENSE).

PicoClaw CMP is an independent open-source frontend. **PicoClaw** and the core binary are
projects of [Sipeed](https://github.com/sipeed/picoclaw); this repository only provides a UI.
