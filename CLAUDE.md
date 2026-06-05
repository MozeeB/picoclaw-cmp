# CLAUDE.md — PicoClaw CMP

## 0. ALWAYS Use Latest Official Docs

**MANDATORY:** Before implementing ANY library feature, use the `docs-lookup` agent (Context7 MCP)
to fetch the current official API documentation. Do NOT rely on training-data knowledge —
APIs change between versions.

Key doc URLs:
- **Compose Multiplatform:** https://www.jetbrains.com/help/kotlin-multiplatform-dev/
- **AndroidX Compose:** https://developer.android.com/jetpack/compose
- **Kotlin coroutines:** https://kotlinlang.org/docs/coroutines-overview.html
- **Koin KMP:** https://insert-koin.io/docs/reference/koin-compose/multiplatform
- **Ktor:** https://ktor.io/docs/
- **Room KMP:** https://developer.android.com/kotlin/multiplatform/room
- **DataStore KMP:** https://developer.android.com/topic/libraries/architecture/datastore
- **AndroidX releases:** https://developer.android.com/jetpack/androidx/releases/

Priority: **Official Google/JetBrains docs first** → library CHANGELOG/README → GitHub Issues.
Prefer official AndroidX/JetBrains libraries over third-party equivalents when both exist.

---

## 1. Project Overview

PicoClaw CMP is the **Kotlin/Compose Multiplatform** version of PicoClaw UI — a cross-platform
management frontend for the PicoClaw service (a local network reverse proxy/tunneling daemon).
It replaces the Flutter-based `picoclaw_fui` with a single Kotlin codebase running natively on
Android, iOS, Desktop (JVM), and Web (WASM/JS).

- **Package:** `com.mozeeb.picoclaw.cmp`
- **Platforms:** Android (min SDK 24), iOS, Desktop (macOS/Windows/Linux via JVM), Web (WasmJS + JS)
- **UI Framework:** Compose Multiplatform 1.11.x
- **Language:** Kotlin 2.3.21
- **Reference implementation:** `picoclaw_fui` (Flutter) at `../picoclaw_fui` — use it as the UI/UX
  and feature specification for every screen and interaction.

### 4 Pages (tabs)

| Index | Name | Purpose |
|-------|------|---------|
| 0 | Dashboard | Service status indicator, start/stop buttons, QR code, public mode toggle, URL display |
| 1 | WebView | Embedded WebView pointing to the PicoClaw local web UI |
| 2 | Logs | Real-time log viewer with filter, auto-scroll, and export |
| 3 | Config | Host/port/path settings, theme picker (6 themes), language selector (12 locales), autostart, binary path |

---

## 2. Architecture — MVI + Koin + Clean Layers

This project uses **MVI (Model-View-Intent)**. All state changes flow through typed intents —
no direct mutation anywhere.

```
View (@Composable)
  │  dispatches ServiceIntent
  ▼
ServiceViewModel
  │  processes intent → produces new ServiceState (immutable)
  ▼
StateFlow<ServiceState>
  │  observed by View via collectAsStateWithLifecycle()
  ▼
View re-composes
```

| Layer | Type | Role |
|-------|------|------|
| **Model** | `data class ServiceState` | Immutable snapshot: status, config, logs, URL, theme, locale |
| **Intent** | `sealed interface ServiceIntent` | `StartService`, `StopService`, `UpdateConfig`, `SelectTheme`, `SelectLocale`, etc. |
| **ViewModel** | `ServiceViewModel : ViewModel` | `fun onIntent(intent: ServiceIntent)` → emits new `StateFlow<ServiceState>` |
| **View** | `@Composable` functions | Reads state, calls `onIntent`, never holds business logic |

### Dependency Injection — Koin

All wiring lives in `di/AppModule.kt`. The `viewModel { }` DSL from `koin-core-viewmodel` binds
`ServiceViewModel`. `koinViewModel()` from `koin-compose-viewmodel` retrieves it in `App.kt`.

Each platform has a `platformModule()` actual that provides `CoreServiceAdapter` and `AppSettings`.
Entry points call `initKoin()` (platform-specific helper in `di/KoinInit.*.kt`) before rendering.

```kotlin
// Each platform entry point:
initKoin()   // starts Koin with platformModule() + appModule
App()        // koinViewModel() resolves ServiceViewModel inside
```

### Settings Persistence — AppSettings Interface

`DataStore<Preferences>` does NOT support JS/WasmJS. A platform-agnostic `AppSettings` interface
bridges the gap:

| Platform | Implementation |
|----------|---------------|
| Android | `DataStoreSettings` backed by `PreferenceDataStoreFactory.create()` |
| Desktop (JVM) | `DataStoreSettings` backed by `PreferenceDataStoreFactory.createWithPath()` |
| iOS | `DataStoreSettings` backed by `PreferenceDataStoreFactory.createWithPath()` |
| JS / WasmJS | `InMemorySettings` (ephemeral map) |

### Adapter Pattern — Platform Services

```
CoreServiceAdapter (interface, commonMain)
  ├── DesktopCoreServiceAdapter (jvmMain)     — validates binary, spawns process, streams stdout
  ├── AndroidCoreServiceAdapter (androidMain) — validates binary, then delegates to PicoClawForegroundService
  ├── IosCoreServiceAdapter (iosMain)         — stub (no binary execution on iOS)
  ├── JsCoreServiceAdapter (jsMain)           — stub
  └── WasmJsCoreServiceAdapter (wasmJsMain)   — stub
```

### ⚠️ Binary Validation — MANDATORY pattern

`CoreServiceAdapter` exposes `validateBinary(customPath)` which returns `BinaryValidation.Ok`
or `BinaryValidation.NotFound`. **Implementations must always validate before spawning** — never
let a raw `IOException` ("No such file or directory") propagate to the user.

```kotlin
// Binary resolution order (Desktop mirrors picoclaw_fui/desktop_core_service_adapter.dart):
// 1. User-configured path (exact file)
// 2. ~/.picoclaw/bin/picoclaw[.exe]
// 3. ./bin/picoclaw[.exe]  (next to app executable)
// 4. ./picoclaw[.exe]      (working directory)
// 5. System PATH entries

// Android resolution order:
// 1. User-configured path
// 2. context.filesDir/bin/picoclaw  (extracted binary)
// 3. nativeLibraryDir/libpicoclaw.so  (packaged SO)
```

When `BinaryValidation.NotFound` is returned:
- `ServiceViewModel.startService()` throws `BinaryNotFoundException` (caught, not re-thrown)
- `ServiceState.binaryFound = false` and `binarySearchedPaths` are set
- `DashboardPage` shows a `BinaryMissingBanner` with the searched paths and a "Go to Config →" button
- `ServiceViewModel` auto-validates on startup and whenever `binaryPath` changes in Config

---

## 3. Library Stack

> **Rules for all dependencies:**
> 1. Always use the latest **stable** version — never alpha/beta/rc.
> 2. Verify **Kotlin 2.3.21 / K2 compiler** compatibility before adding. Check the library's
>    GitHub releases page or CHANGELOG for "Kotlin 2.x" or "K2" support.
> 3. KSP version must match Kotlin: `ksp = "2.3.21-<ksp-build>"` —
>    see https://github.com/google/ksp/releases
> 4. Do NOT use version numbers from training data — fetch from the official releases page first.

| Library | Artifact | Version | Purpose | When to use |
|---------|----------|---------|---------|-------------|
| Compose Multiplatform | `org.jetbrains.compose` | 1.11.x | UI framework | Always |
| AndroidX Lifecycle ViewModel | `lifecycle-viewmodel-compose` | 2.11.x | MVI ViewModel + `collectAsStateWithLifecycle` | Always |
| **Koin core** | `io.insert-koin:koin-core` | **4.1.0** | DI core — supports all KMP targets including iOS | Always |
| **Koin compose-viewmodel** | `io.insert-koin:koin-compose-viewmodel` | **4.1.0** | `koinViewModel()` for all KMP targets | Always |
| **Koin Android** | `io.insert-koin:koin-android` | **4.1.0** | Android-specific Koin setup | androidMain only |
| **AndroidX DataStore** | `androidx.datastore:datastore-preferences-core` | 1.1.7 | Prefs (theme, locale, port) — official KMP | android/jvm/ios only |
| **Ktor** | `io.ktor:ktor-client-core` + platform engine | 3.x | HTTP/WebSocket (health checks, telemetry) | Only if needed |
| **Room KMP** | `androidx.room:room-runtime` + KSP compiler | 2.7.x | Structured persistence (log entries) | Only if needed |
| SQLite Bundled | `androidx.sqlite:sqlite-bundled` | 2.5.x | SQLite driver for Room KMP | With Room |
| kotlinx.serialization | `kotlinx-serialization-json` | 1.8.x | JSON (config export/import) | For JSON |
| kotlinx.coroutines | `kotlinx-coroutines-core` | 1.11.x | Coroutines, Flow, StateFlow | Always |

### Forbidden libraries
- `multiplatform-settings` — replaced by official AndroidX DataStore + `InMemorySettings` stub
- `qrose` or any third-party QR library — in-house `QrCodeCanvas.kt` implementation instead
- `Gson`, `Moshi` — use `kotlinx.serialization`
- `LiveData` — use `StateFlow`
- `RxJava` — use coroutines/Flow
- Koin versions < 4.1.0 — earlier versions lack iOS (`iosArm64`) klib on Maven Central

### ⚠️ Critical Koin Version Note

**Use Koin 4.1.0 exactly.** Earlier versions have missing platform support:
- `4.0.0`: Missing `iosArm64` klib → iOS compile fails
- `3.5.x`: Missing `wasmJs` support + different artifact names
- `4.1.0`: ✅ Full support for Android, JVM, iOS, JS, WasmJS

The correct Koin artifacts for this project:
```toml
koin = "4.1.0"
koin-core            = "io.insert-koin:koin-core"
koin-compose-viewmodel = "io.insert-koin:koin-compose-viewmodel"  # provides koinViewModel()
koin-android         = "io.insert-koin:koin-android"
```

### ⚠️ DataStore Platform Support

`androidx.datastore:datastore-preferences-core` supports Android, JVM, iOS.
**It does NOT support JS or WasmJS.** Add it only to android/jvm/ios source sets:
```kotlin
androidMain.dependencies { implementation(libs.datastore.preferences) }
jvmMain.dependencies    { implementation(libs.datastore.preferences) }
iosMain.dependencies    { implementation(libs.datastore.preferences) }
// jsMain / wasmJsMain → use InMemorySettings instead
```

---

## 4. Feature Roadmap

### Phase 1 — Foundation ✅ Complete
- [x] MVI: `ServiceState`, `ServiceIntent`, `ServiceViewModel`
- [x] `CoreServiceAdapter` (interface) + stubs for all 5 platforms
- [x] `AppSettings` interface + `DataStoreSettings` (android/jvm/ios) + `InMemorySettings` (js/wasmJs)
- [x] `SettingsRepository` (DataStore wrapper via `AppSettings`)
- [x] `AppModule.kt` (Koin module), `PlatformModule.kt` (per-platform actual)
- [x] `initKoin()` helpers for all 5 platform entry points
- [x] `AppTheme.kt` — 6 `ColorScheme` definitions
- [x] `App.kt` — root composable with `NavigationSuiteScaffold` + 4 pages + `koinViewModel()`

### Phase 2 — Dashboard + Config Pages ✅ Complete
- [x] `DashboardPage.kt` — status, start/stop, URL, QR code, public mode toggle
- [x] `QrCodeCanvas.kt` — in-house QR encoder + Compose Canvas renderer
- [x] `ConfigPage.kt` — host/port fields, theme picker, language selector
- [x] `DesktopCoreServiceAdapter.kt` — process spawn + stdout stream
- [x] `AndroidCoreServiceAdapter.kt` + `PicoClawForegroundService.kt`

### Phase 3 — Logs + WebView ✅ Complete
- [x] `LogPage.kt` — `LazyColumn` log viewer, auto-scroll, filter, export
- [x] `WebViewPage.kt` + `PlatformWebView.kt` (expect/actual for all 5 platforms)
  - Android: native `WebView` via `AndroidView`
  - Desktop: opens in system browser (`java.awt.Desktop`)
  - iOS/Web: stub with TODO note

### Phase 4 — Platform Polish ✅ Complete
- [x] Desktop: system tray (Compose `Tray`) — show/hide window, start/stop service, quit; minimize-to-tray on close
- [x] Desktop: window size/position persistence via `WindowStateStore` (AppSettings/DataStore)
- [x] Android: `PicoClawForegroundService` (Phase 2) + `BootReceiver` auto-start when `autoStart` enabled
- [x] Localization: in-house type-safe i18n (`i18n/AppStrings.kt`) for all 12 locales
- [x] Analytics stub: `Analytics` interface + `NoOpAnalytics` + `AndroidAnalytics` (Firebase-ready), gated by telemetry toggle
- [x] iOS: `WKWebView` via `UIKitView` in `PlatformWebView.ios.kt`

> **Desktop tray + UI share one `ServiceViewModel`.** `App(viewModel = koinViewModel())` accepts an
> injected VM; the desktop entry point constructs it once (`ServiceViewModel(koin.get(), koin.get(), koin.get())`)
> and passes it to both the `Tray` and the `Window` so tray-initiated start/stop stays in sync with the UI.
> `jvm initKoin()` returns the `Koin` instance for this.

---

## 5. Theming — 6 AppThemeModes

Defined in `ui/AppTheme.kt` as `enum class AppThemeMode`. Colors match `picoclaw_fui` exactly.

| Mode | Primary (sidebar) | PrimaryContainer (content bg) | Secondary (accent) | Dark? |
|------|------------------|-----------------------------|--------------------|-------|
| `Carbon` | `#111111` | `#1A1A1A` | `#00E5FF` | yes |
| `Slate` | `#020617` | `#0F172A` | `#F59E0B` | yes |
| `Obsidian` | `#000000` | `#111111` | `#FFFFFF` | yes |
| `Ebony` | `#0C0C0C` | `#1A1A1A` | `#FACC15` | yes |
| `Nord` | `#2E3440` | `#3B4252` | `#88C0D0` | yes |
| `Sakura` | `#AD1457` | `#FDF2F8` | `#C2185B` | no (light) |

Font family: **Inter** — bundle as a font resource in `composeResources/font/`.

Rules:
- All colors accessed via `MaterialTheme.colorScheme.*` in composables — never hardcode hex.
- Minimum contrast ratio: **4.5:1** for body text on background.
- Support `isSystemInDarkTheme()` as default; manual override stored in DataStore.

---

## 6. Localization — in-house type-safe i18n

12 supported locales (same as `picoclaw_fui`):
`en`, `zh`, `es`, `fr`, `de`, `ru`, `pt`, `ja`, `ko`, `id`, `ar`, `hi`

**Why in-house instead of `composeResources` strings.xml:** manual (in-app) locale override for
compose-resources is version-sensitive across CMP releases. A type-safe `AppStrings` bundle keyed
on `state.locale` switches language instantly on every platform with zero platform locale plumbing.

| File | Role |
|------|------|
| `i18n/AppStrings.kt` | `data class AppStrings` (all keys) + canonical `StringsEn` + `stringsFor(locale)` + `LocalStrings` CompositionLocal |
| `i18n/AppStringsLocales1.kt` | zh, es, fr, de, ru |
| `i18n/AppStringsLocales2.kt` | pt, ja, ko, id, ar, hi |

- Provided once at the App root: `CompositionLocalProvider(LocalStrings provides stringsFor(state.locale))`.
- Read in any composable via `LocalStrings.current.<key>` (e.g. `s.start`, `s.dashboardTitle`).
- Changing `ServiceIntent.SelectLocale` updates `state.locale` → recomposition → new bundle.
- Unknown locales fall back to `StringsEn`. To add a key: add the field + a value in every bundle.
- Language names in the picker are **endonyms** (English, 中文, …) — intentionally not translated.

---

## 7. Platform-Specific Feature Matrix

| Feature | Android | Desktop (JVM) | iOS | Web |
|---------|---------|--------------|-----|-----|
| Service start/stop | ✅ `PicoClawForegroundService` | ✅ Process spawn | Stub | Stub |
| System tray | N/A | ✅ Compose `Tray` | N/A | N/A |
| Window management | `WindowManager` | ✅ `ComposeWindow` + `WindowStateStore` | UIKit | Browser |
| WebView | ✅ `android.webkit.WebView` | Opens in system browser | ✅ `WKWebView` (UIKitView) | Stub |
| Auto-start on boot | ✅ `BootReceiver` | OS startup (future) | N/A | N/A |
| Notifications | ✅ foreground notification | future | N/A | N/A |
| Analytics | ✅ `AndroidAnalytics` (Firebase-ready) | `NoOpAnalytics` | `NoOpAnalytics` | `NoOpAnalytics` |
| Localization (12 locales) | ✅ in-house `AppStrings` | ✅ | ✅ | ✅ |
| DataStore | ✅ `DataStoreSettings` | ✅ `DataStoreSettings` | ✅ `DataStoreSettings` | `InMemorySettings` |

Stub actuals call `println("WARN: [feature] not supported on [platform]")`.

### ⚠️ Android cleartext (WebView) — required for local HTTP

Android ≥ 9 (API 28) blocks cleartext HTTP by default, but the PicoClaw web UI is plain `http://`.
Two coordinated pieces keep the embedded WebView working:

1. **`androidApp/src/main/res/xml/network_security_config.xml`** permits cleartext **only to loopback**
   (`127.0.0.1`, `localhost`, `0.0.0.0`), referenced via `android:networkSecurityConfig` in the manifest.
2. **The WebView loads `ServiceState.localWebUrl`, not `webUrl`.** `localWebUrl` always targets the
   loopback host (`127.0.0.1`) — the binary binds `0.0.0.0` in public mode, so loopback is always
   reachable on-device, and it stays inside the cleartext allow-list. `webUrl` (which uses the LAN IP
   in public mode) is reserved for the QR code / shareable URL only.

Regression to avoid: pointing the embedded WebView at `webUrl` (LAN IP) → `ERR_CLEARTEXT_NOT_PERMITTED`.

---

## 8. In-House QR Code (`QrCodeCanvas.kt`)

**No third-party QR libraries.** QR code generation is implemented entirely in `commonMain`.

- **File:** `shared/src/commonMain/kotlin/com/mozeeb/picoclaw/cmp/ui/widgets/QrCodeCanvas.kt`
- **Encoder:** Pure Kotlin QR matrix encoder (ISO/IEC 18004, Mode: byte, ECC: M, versions 1–10) → `Array<BooleanArray>`
- **Renderer:** `@Composable fun QrCodeImage(data: String, modifier: Modifier = Modifier)` — draws the bit matrix using Compose `Canvas` + `drawRect` per module. Works identically on all platforms.

Usage:
```kotlin
QrCodeImage(
    data = "http://127.0.0.1:18800",
    modifier = Modifier.size(180.dp)
)
```

---

## 9. File Conventions

### Package structure (under `com.mozeeb.picoclaw.cmp`)
```
mvi/
  ServiceState.kt       # data class — immutable model
  ServiceIntent.kt      # sealed interface — all user/system intents
  ServiceViewModel.kt   # ViewModel — processes intents, emits StateFlow
core/
  AppSettings.kt                  # interface — platform-agnostic settings
  CoreServiceAdapter.kt           # interface (commonMain)
  SettingsRepository.kt           # AppSettings wrapper
  UiConstants.kt                  # layout constants (dp values)
di/
  AppModule.kt                    # Koin common module
  PlatformModule.kt               # expect — per-platform Koin bindings
  DataStoreFactory.kt             # platform factory (android/jvm/ios only)
  KoinInit.kt                     # per-platform initKoin() helper
data/
  AppDatabase.kt                  # Room database (Phase 4, if needed)
ui/
  App.kt                          # root composable — uses koinViewModel()
  AppTheme.kt                     # 6 ColorScheme definitions + MaterialTheme
  pages/
    DashboardPage.kt
    WebViewPage.kt
    LogPage.kt
    ConfigPage.kt
    PlatformWebView.kt            # expect/actual WebView
  widgets/
    AdaptiveNavBar.kt             # BoxWithConstraints-based compact/wide nav
    QrCodeCanvas.kt               # in-house QR encoder + renderer
```

Platform source sets follow the same package structure with `actual` declarations:
- `androidMain`, `jvmMain`, `iosMain`, `jsMain`, `wasmJsMain`

### Rules
- **800 lines max** per file. Extract to sub-files if exceeded.
- **One `@Composable` screen per file** in `ui/pages/`.
- **One concern per file** — no mixing ViewModel logic into UI files.
- All `expect` declarations must have actuals for: `androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`.
- No-op stubs are acceptable for unsupported platforms; they must log a warning.
- Never use `if (Platform.isAndroid)` in `commonMain` — use `expect/actual`.

---

## 10. Build Commands

```bash
# Android
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug

# Desktop (JVM)
./gradlew :desktopApp:run
./gradlew :desktopApp:hotRun --auto   # hot-reload

# Web (WasmJS — recommended)
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# Web (JS)
./gradlew :webApp:jsBrowserDevelopmentRun

# All tests
./gradlew :shared:allTests

# JVM tests only (fastest)
./gradlew :shared:jvmTest

# Android unit tests
./gradlew :shared:testAndroidHostTest

# Compile all platforms (verification)
./gradlew :shared:compileKotlinJvm :shared:compileKotlinIosArm64 \
  :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileAndroidMain
```

---

## 11. Development Workflow

Every feature follows this pipeline — no exceptions.

### Step 0 — Research & Official Docs First

**MANDATORY before writing any code:**
1. Use `docs-lookup` agent (Context7 MCP) to fetch current API docs for every library involved.
2. Do NOT use training-data knowledge for library APIs — fetch live docs every time.
3. Search GitHub for existing KMP/Compose implementations before writing new code.
4. Check `gradle/libs.versions.toml` — prefer libraries already declared before adding new ones.
5. Prefer official Google/JetBrains libraries. Implement in-house if clean solution < 300 lines.

### Step 1 — Plan

Use the `planner` agent for any feature touching more than 2 files. Break work into:
`architecture → ViewModel → UI → platform actuals → tests`

### Step 2 — TDD (Red → Green → Refactor)

1. Write the failing test first (RED).
2. Run `./gradlew :shared:jvmTest` — must FAIL.
3. Write minimal implementation (GREEN).
4. Run tests — must PASS.
5. Refactor — run tests again.
6. Verify coverage ≥ 80%.

### Step 3 — Code Review

Use `kotlin-reviewer` agent immediately after writing Kotlin code.
Address all CRITICAL and HIGH findings before proceeding.

### Step 4 — Platform Verification

Before marking a feature complete, compile all 5 targets:
```bash
./gradlew :shared:compileKotlinJvm :shared:compileKotlinIosArm64 \
  :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileAndroidMain
```

### Step 5 — Commit

```
feat: add DashboardPage with service status and QR code
fix: correct port conflict detection in ServiceViewModel
refactor: extract AdaptiveNavBar into shared widget
test: add ServiceViewModel intent transition tests
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`

---

## 12. UI/UX Rules — Mobile & Adaptive Design

### Touch & Spacing
- Minimum touch target: **48×48 dp**. Use `Modifier.minimumInteractiveComponentSize()`.
- Spacing base unit: **4 dp**. Standard increments: 4, 8, 12, 16, 24, 32, 48 dp.
- Extract all layout constants to `core/UiConstants.kt` (sidebar width: 80 dp, icon: 48 dp, card radius: 12 dp).
- Gap between adjacent touch targets: ≥ 8 dp.

### Adaptive Navigation (BoxWithConstraints)

The `AdaptiveNavBar` widget uses `BoxWithConstraints` to switch layout at the 600 dp breakpoint:

| Window Width | Component | Use case |
|---|---|---|
| Compact < 600 dp | `NavigationBar` (bottom) | Mobile portrait |
| Wide ≥ 600 dp | `NavigationRail` (left side) | Tablet, desktop |

- Navigation background = `MaterialTheme.colorScheme.primary` (dark sidebar color)
- Selected icon = `MaterialTheme.colorScheme.secondary` (accent color)
- Same 4 destinations: Dashboard, WebView, Logs, Config

### Safe Area & Insets
- Apply `Modifier.systemBarsPadding()` at the root scaffold level.
- Use `Modifier.imePadding()` on scrollable containers that contain text fields (Config page).
- Use `Modifier.windowInsetsPadding(WindowInsets.displayCutout)` for notched screens.

### Typography
- Use only `MaterialTheme.typography.*` — never hardcode font sizes.
- `displayLarge`/`headlineLarge`: page titles; `titleMedium`: card headers; `bodyLarge`: content; `labelMedium`: buttons/chips.
- Font family: **Inter** (bundled resource file in `composeResources/font/`).

### Color & Theme
- All colors from `MaterialTheme.colorScheme.*` — never hardcode hex values in composables.
- Minimum contrast ratio: **4.5:1** for body text on background.
- Default to `isSystemInDarkTheme()`; manual override stored in DataStore.

### Animations
- Navigation tab switch: `AnimatedContent` with `slideInVertically` (300 ms, `FastOutSlowIn`) — mirrors Flutter's `SharedAxisTransition.vertical`.
- Service status pulsing: `rememberInfiniteTransition` (1000 ms cycle) when `ServiceStatus.Starting`.
- Property changes (color, alpha, size): `animate*AsState()` — 150–300 ms.
- Visibility toggles: `AnimatedVisibility` — 200 ms fade + expand.

### Loading / Error / Empty States
- **Loading:** `CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)` centered.
- **Error:** `Card` with error icon + localized message + Retry `TextButton`.
- **Empty:** Centered icon + descriptive `Text`.
- **Transient errors:** `Snackbar` via `SnackbarHostState` — 3 s duration.

### Accessibility
- Every `Icon`/`Image`: non-null `contentDescription`, or `Modifier.semantics { invisibleToUser() }`.
- Custom tap areas: `Modifier.semantics { role = Role.Button }`.
- Toggle state: `Modifier.semantics { stateDescription = if (on) "enabled" else "disabled" }`.
- Verify semantics tree in tests: `composeTestRule.onRoot().printToLog("TAG")`.

---

## 13. Latest Best Practices (Enforced)

Always use the latest stable idioms. Do NOT use deprecated patterns even if older docs show them.

### Kotlin
- `data class` with `copy()` for all state — never mutable `var` on domain objects.
- `sealed interface` for exhaustive `when` (intents, outcomes).
- `value class` for type-safe wrappers (`value class Port(val value: Int)`).
- `StateFlow` for UI state; `SharedFlow` for one-shot events — never `LiveData`.
- `suspend fun` + coroutines — never `Thread.sleep` or RxJava.
- `Result<T>` or sealed `Outcome<T>` for error propagation — never throw across async boundaries.
- `@OptIn` to explicitly adopt experimental KMP APIs rather than suppressing globally.

### Compose Multiplatform
- State hoisting: hoist to lowest common ancestor; prefer stateless composables.
- `remember { }` for expensive objects; `rememberSaveable { }` for rotation-surviving state.
- `LazyColumn` / `LazyRow` for any list > 5 items.
- `derivedStateOf { }` to avoid recompositions from high-frequency state.
- `Modifier` as the first unnamed parameter after required params; extend with `.then(modifier)` internally.
- `@Stable` / `@Immutable` on custom state classes to enable Compose skipping.
- `CompositionLocalProvider` for cross-cutting concerns (theme, locale) — avoid deep lambda passing.

### KMP / Multiplatform
- All platform-divergent code in `expect/actual` — no `if (Platform.isAndroid)` in `commonMain`.
- `Dispatchers.Default` for CPU work, `Dispatchers.IO` for I/O in `commonMain`.
- `kotlinx.serialization` for all JSON — never Gson or Moshi.
- Separate `androidMain` and `jvmMain` source sets.

### Dependency & Build
- Prefer official Google/JetBrains libraries. Implement in-house if clean < 300 lines.
- **Latest stable only** — never alpha/beta/rc. Verify on official releases page before using.
- **Verify Kotlin 2.3.21 / K2 compatibility** — check library CHANGELOG for "Kotlin 2.x" or "K2" support before adding.
- **KSP must match Kotlin:** `ksp = "2.3.21-<build>"` — see https://github.com/google/ksp/releases
- All versions in `gradle/libs.versions.toml` — no version literals in `build.gradle.kts`.
- `implementation` only; avoid `api` unless publishing a public library.

---

## 14. Binary Validation Rules (CRITICAL — prevents crash on all platforms)

**Never call `ProcessBuilder.start()` without first checking the binary exists.**

### Rule
```kotlin
// ✅ CORRECT: validate first, throw BinaryNotFoundException with guidance
val validation = adapter.validateBinary(binaryPath)
if (validation is BinaryValidation.NotFound) throw BinaryNotFoundException(validation.searchedPaths)

// ❌ WRONG: blind ProcessBuilder call that throws raw IOException to the user
ProcessBuilder(cmd).start()  // crashes if binary missing
```

### ⚠️ Binary invocation format (must match picoclaw_fui)
Launch the binary as **`<binary> -port <port> [extraArgs]`** — single-dash `-port`, no
`--host`/`--path` flags (the real CLI rejects those, so the server never binds → the WebView shows
`ERR_CONNECTION_REFUSED`). Public mode appends `-public` to the args (via `buildEffectiveArgs`),
which is how the binary listens on all interfaces. The embedded WebView always targets loopback
and auto-retries the first load to cover the start-to-bind window.

### Validation chain
1. `ServiceViewModel.startService()` calls `adapter.validateBinary()` before `adapter.start()`
2. `adapter.start()` re-validates internally (second safety net)
3. `PicoClawForegroundService.handleStart()` checks binary existence with `runCatching` — never crashes the service

### State fields used for binary feedback
- `ServiceState.binaryFound: Boolean?` — null = unvalidated, true = found, false = missing
- `ServiceState.binarySearchedPaths: List<String>` — shown in the UI banner
- `ServiceState.isBinaryMissing: Boolean` — shorthand for `binaryFound == false`

### UI guidance
- `DashboardPage` shows a `BinaryMissingBanner` when `state.isBinaryMissing`
- Banner shows the searched paths (monospace, truncated to 4)
- "Go to Config →" button navigates to the Config tab (`selectedIndex = 3`)
- `ServiceViewModel` auto-validates on init and on every `UpdateBinaryPath` intent

### Platform resolution order (mirrors Flutter's `_resolveCoreExePath`)
| Platform | Candidates checked in order |
|----------|----------------------------|
| Desktop | user path → `~/.picoclaw/bin/picoclaw` → `./bin/picoclaw` → `./picoclaw` → PATH |
| Android | user path → `nativeLibraryDir/libpicoclaw.so` → `filesDir/libpicoclaw.so` (+ APK extraction) |
| iOS / Web | always returns `BinaryValidation.NotFound` (not supported) |

---

## 14b. Binary Acquisition — Download, Browse, Validate

The binary can be missing on first run (Flutter bundles it at BUILD time via
`tools/fetch_core_local.dart`; CMP improves on this with a **runtime downloader**).

### Runtime download (`BinaryDownloader`)
- **Interface:** `core/BinaryDownloader.kt` — `suspend fun downloadLatest(onProgress): DownloadResult`
- **Shared logic:** `jvmCommonMain/JvmBinaryDownloaderBase.kt` (Desktop + Android share it via the
  `jvmCommon` intermediate source set). Uses **only** `java.net.HttpURLConnection` +
  `kotlinx.serialization` + `java.util.zip` — **no third-party HTTP/download library.**
- Fetches `github.com/sipeed/picoclaw` `releases/latest`, scores assets by
  `picoclaw_<platform>_<arch>.{zip,tar.gz}` (mirrors Flutter's `selectBestAsset`), downloads,
  extracts the executable (zip + tar.gz), installs + `chmod +x`.

| Platform | Install target | Supported |
|----------|---------------|-----------|
| Desktop (JVM) | `~/.picoclaw/bin/picoclaw[.exe]` | ✅ `DesktopBinaryDownloader` |
| Android | `filesDir/libpicoclaw.so` | ✅ `AndroidBinaryDownloader` |
| iOS / JS / WasmJS | — | ❌ returns `DownloadResult.Unsupported` |

### MVI wiring
- `ServiceIntent.DownloadBinary` → `ServiceViewModel.downloadBinary()` → updates
  `ServiceState.isDownloading` / `downloadProgress`, then on success sets `binaryPath`,
  saves config, and re-validates.
- `ServiceState.isDownloadSupported` gates the Download button visibility.
- `ServiceIntent.PickBinaryFile` → `pickBinaryFile()` (expect/actual): Desktop uses
  `JFileChooser`; Android/iOS/Web return null (Android uses Download instead).

### UI
- `DashboardPage` `BinaryMissingBanner`: **Download binary** button + progress bar + "Config →".
- `ConfigPage` Binary section: **Browse / Validate / Download** buttons + status row + progress.

### ⚠️ The jvmCommon source set
`shared/build.gradle.kts` declares `applyDefaultHierarchyTemplate()` **explicitly** before
creating `jvmCommonMain` manually — otherwise the manual source set disables the default
template and breaks `iosMain → iosArm64` actual wiring. Do not remove that line.

---

## 14c. Public Mode (CRITICAL — mirrors picoclaw_fui ServiceManager)

"Public mode" exposes the service to the LAN so another device can scan the QR / open the URL.
It involves THREE coordinated changes — all three are required or it silently fails:

1. **Bind address** — `ServiceState.bindHost` = `0.0.0.0` when public (else the configured host).
   `startService()` passes `bindHost` (not `host`) to the adapter.
2. **`-public` flag** — `buildEffectiveArgs()` appends `-public` to the user args when public mode
   is on (deduplicated), so the binary actually listens on all interfaces.
3. **Display IP** — `TogglePublicMode(true)` calls `adapter.getDeviceIpAddress()` and stores it in
   `ServiceState.deviceIp`. `ServiceState.displayHost`/`webUrl`/QR then use the LAN IP (not
   `0.0.0.0`, which is un-scannable). Toggling off clears `deviceIp`.

Also: `publicMode` is **persisted** (`SettingsRepository.savePublicMode` / `KEY_PUBLIC_MODE`), and
`loadSettings()` re-fetches the device IP on launch when public mode was previously enabled.

Common regression: setting only `publicMode = true` without fetching the IP / adding `-public` →
QR shows `127.0.0.1` and the binary stays on localhost. Never do that.

---

## 15. In-House QR Code Rules

- File: `ui/widgets/QrCodeCanvas.kt` in `commonMain`
- Encoder: pure Kotlin, no dependencies — implements QR matrix for byte-mode URLs, versions 1–10, ECC M
- Renderer: Compose `Canvas` + `drawRect` per module cell
- Unit test: verify encoder output dimensions for a known URL (see `commonTest`)
- Do NOT replace with any third-party QR library

---

## 15. Unit Testing Rules

Every feature MUST have tests written BEFORE implementation (TDD).

### Coverage requirements
- **80% minimum** — all `commonMain` code
- **100%** — `mvi/ServiceState.kt`, `mvi/ServiceIntent.kt`, `mvi/ServiceViewModel.kt`
- Every `expect` function: at least one test per platform

### Test placement
| Source set | Test location |
|---|---|
| `commonMain` | `commonTest` |
| `androidMain` | `androidHostTest` |
| `jvmMain` | `jvmTest` |
| `iosMain` | `iosTest` |

### Coroutine test setup (required)
`ServiceViewModel` uses `viewModelScope` which depends on `Dispatchers.Main`. Tests MUST
set `Dispatchers.Main` to the test dispatcher to prevent race conditions:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }
}
```

Use `runTest` + `advanceUntilIdle()` before dispatching intents so the VM's init `loadSettings()`
coroutine completes first.

### What to test per layer
- **MVI ViewModel:** Dispatch each intent from known initial state → assert new state is correct and is a different object (immutability).
- **Binary validation:** `FakeAdapter(binaryValid = false)` → `StartService` → assert `binaryFound == false`, `isBinaryMissing == true`, `errorMessage != null`.
- **Binary found:** `FakeAdapter(binaryValid = true)` → `StartService` → assert `binaryFound == true`.
- **SettingsRepository:** Read/write round-trips via `FakeAppSettings`.
- **CoreServiceAdapter:** Mock process lifecycle → assert `ServiceStatus` transitions.
- **AppTheme:** Each `AppThemeMode` returns non-null `ColorScheme` with correct `secondary` color.
- **QrCodeCanvas:** Encoder produces correct matrix dimensions for a known URL.
- **UI composables:** `ComposeUiTest` — renders without crash, key nodes present.

### Test naming
```kotlin
// given_<state>_when_<action>_then_<result>
@Test fun given_stopped_when_startIntent_then_statusIsStarting() { }
```

### Running tests
```bash
./gradlew :shared:allTests            # all platforms
./gradlew :shared:jvmTest             # JVM only (fastest)
./gradlew :shared:testAndroidHostTest # Android unit tests
```

### Forbidden
- Do NOT test implementation details — test state transitions via public API only.
- Do NOT mock `StateFlow` internals — test through `onIntent` / `state`.
- Do NOT leave `@Ignore` without a comment linking to an issue.

---

## 16. Verification Checklist

Before marking any feature complete:

- [ ] `./gradlew :shared:jvmTest` passes (all 35 tests green)
- [ ] `./gradlew :shared:testAndroidHostTest` passes
- [ ] All 5 KMP targets compile:
  ```bash
  ./gradlew :shared:compileKotlinJvm :shared:compileKotlinIosArm64 \
    :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileAndroidMain
  ```
- [ ] `./gradlew :androidApp:assembleDebug` builds the APK
- [ ] `./gradlew :desktopApp:jar` builds the Desktop JAR
- [ ] `./gradlew :webApp:wasmJsBrowserDevelopmentWebpack` builds the Web bundle
- [ ] No hardcoded colors, strings, or sizes in composables
- [ ] All `expect` declarations have actuals for all 5 targets
- [ ] `kotlin-reviewer` agent run; CRITICAL/HIGH issues fixed
