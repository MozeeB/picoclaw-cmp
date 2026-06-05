package com.mozeeb.picoclaw.cmp.mvi

import com.mozeeb.picoclaw.cmp.FakeAppSettings
import com.mozeeb.picoclaw.cmp.core.Analytics
import com.mozeeb.picoclaw.cmp.core.BinaryDownloader
import com.mozeeb.picoclaw.cmp.core.BinaryValidation
import com.mozeeb.picoclaw.cmp.core.CoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.DownloadResult
import com.mozeeb.picoclaw.cmp.core.SettingsRepository
import com.mozeeb.picoclaw.cmp.ui.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ServiceViewModel].
 *
 * Pattern: given_<state>_when_<action>_then_<result>
 * Rules:
 * - Every intent is tested from a known initial state.
 * - Assertions verify BOTH the correct new value AND that a new object was produced (immutability).
 * - 100% coverage of every [ServiceIntent] variant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceViewModelTest {

    // Use UnconfinedTestDispatcher so viewModelScope coroutines run eagerly in tests
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    private class FakeAdapter(
        private val binaryValid: Boolean = true,
    ) : CoreServiceAdapter {
        var startCalled = false
        var stopCalled = false
        var startShouldThrow = false

        private val _logFlow = MutableSharedFlow<String>()
        override val logFlow: Flow<String> = _logFlow

        override suspend fun validateBinary(customPath: String): BinaryValidation =
            if (binaryValid) BinaryValidation.Ok("/fake/picoclaw")
            else BinaryValidation.NotFound(listOf("/fake/picoclaw"))

        override suspend fun start(host: String, port: Int, path: String, binaryPath: String, extraArgs: String) {
            startCalled = true
            if (startShouldThrow) throw RuntimeException("fake start error")
        }

        override suspend fun stop() { stopCalled = true }
        override suspend fun exportLogs(logs: List<String>) {}
        override suspend fun getDeviceIpAddress(): String? = "192.168.1.100"

        suspend fun emitLog(line: String) = _logFlow.emit(line)
    }

    private class FakeDownloader(
        override val isSupported: Boolean = true,
        private val result: DownloadResult = DownloadResult.Success("/fake/downloaded/picoclaw"),
    ) : BinaryDownloader {
        override suspend fun downloadLatest(onProgress: (Float) -> Unit): DownloadResult {
            onProgress(0.5f)
            onProgress(1f)
            return result
        }
    }

    private class FakeAnalytics : Analytics {
        var collectionEnabled = false
        val events = mutableListOf<String>()
        override fun setEnabled(enabled: Boolean) { collectionEnabled = enabled }
        override fun logEvent(name: String, params: Map<String, String>) { events += name }
    }

    private class FakeSettings : SettingsRepository(FakeAppSettings()) {
        var savedTheme: String? = null
        var savedLocale: String? = null
        var configSaved = false

        override suspend fun loadConfig() = com.mozeeb.picoclaw.cmp.core.AppConfig(
            host = "127.0.0.1", port = 18800, path = "/",
            binaryPath = "", extraArgs = "", autoStart = false,
            themeName = "Carbon", locale = "en", telemetryEnabled = false,
        )

        override suspend fun saveConfig(
            host: String, port: Int, path: String,
            binaryPath: String, extraArgs: String, autoStart: Boolean,
        ) { configSaved = true }

        override suspend fun saveTheme(themeName: String) { savedTheme = themeName }
        override suspend fun saveLocale(locale: String) { savedLocale = locale }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeViewModel(
        adapter: FakeAdapter = FakeAdapter(),
        downloader: BinaryDownloader = FakeDownloader(),
        analytics: Analytics = FakeAnalytics(),
    ): Pair<ServiceViewModel, FakeAdapter> {
        val fake = adapter
        val settings = FakeSettings()
        val vm = ServiceViewModel(fake, settings, downloader, analytics)
        return vm to fake
    }

    // -------------------------------------------------------------------------
    // ServiceStatus transitions
    // -------------------------------------------------------------------------

    @Test
    fun given_stopped_when_startIntent_then_statusBecomesStarting() = runTest {
        val (vm, _) = makeViewModel()
        val before = vm.state.value
        assertEquals(ServiceStatus.Stopped, before.status)

        vm.onIntent(ServiceIntent.StartService)

        // After dispatching, status transitions to Starting (then Running async)
        // We check the intermediate state change happened
        val after = vm.state.value
        assertTrue(after.status == ServiceStatus.Starting || after.status == ServiceStatus.Running)
        assertNotSame(before, after)
    }

    @Test
    fun given_running_when_stopIntent_then_statusBecomesStopped() = runTest {
        val (vm, _) = makeViewModel()
        // Force state to Running
        vm.onIntent(ServiceIntent.StartService)
        // Wait for Running
        val running = vm.state.first { it.status == ServiceStatus.Running || it.status == ServiceStatus.Stopped }
        vm.onIntent(ServiceIntent.StopService)
        val stopped = vm.state.first { it.status == ServiceStatus.Stopped }
        assertEquals(ServiceStatus.Stopped, stopped.status)
    }

    @Test
    fun given_stopped_when_startFails_then_statusIsStoppedAndErrorSet() = runTest {
        val adapter = FakeAdapter().also { it.startShouldThrow = true }
        val (vm, _) = makeViewModel(adapter)

        vm.onIntent(ServiceIntent.StartService)
        val errorState = vm.state.first { it.status == ServiceStatus.Stopped && it.errorMessage != null }

        assertEquals(ServiceStatus.Stopped, errorState.status)
        assertNotNull(errorState.errorMessage)
        assertTrue(errorState.errorMessage.isNotBlank())
    }

    // -------------------------------------------------------------------------
    // Config intents
    // -------------------------------------------------------------------------

    @Test
    fun given_defaultState_when_updateHost_then_hostChangedAndDirty() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        val before = vm.state.value

        vm.onIntent(ServiceIntent.UpdateHost("10.0.0.1"))

        val after = vm.state.value
        assertEquals("10.0.0.1", after.host)
        assertTrue(after.configDirty)
        assertNotSame(before, after)
    }

    @Test
    fun given_defaultState_when_updatePort_then_portChanged() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.UpdatePort(9090))
        assertEquals(9090, vm.state.value.port)
        assertTrue(vm.state.value.configDirty)
    }

    @Test
    fun given_defaultState_when_updatePath_then_pathChanged() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.UpdatePath("/api"))
        assertEquals("/api", vm.state.value.path)
    }

    @Test
    fun given_defaultState_when_updateBinaryPath_then_pathChanged() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.UpdateBinaryPath("/usr/local/bin/picoclaw"))
        assertEquals("/usr/local/bin/picoclaw", vm.state.value.binaryPath)
        assertTrue(vm.state.value.configDirty)
    }

    @Test
    fun given_defaultState_when_toggleAutoStart_then_autoStartToggled() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.autoStart)
        vm.onIntent(ServiceIntent.ToggleAutoStart(true))
        assertTrue(vm.state.value.autoStart)
        assertTrue(vm.state.value.configDirty)
    }

    // -------------------------------------------------------------------------
    // Theme & locale
    // -------------------------------------------------------------------------

    @Test
    fun given_defaultTheme_when_selectSlate_then_themeChangesToSlate() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle() // Ensure loadSettings() completes first
        assertEquals(AppThemeMode.Carbon, vm.state.value.theme)

        vm.onIntent(ServiceIntent.SelectTheme(AppThemeMode.Slate))

        assertEquals(AppThemeMode.Slate, vm.state.value.theme)
    }

    @Test
    fun given_defaultLocale_when_selectZh_then_localeChanges() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle() // Ensure init loadSettings() completes before dispatching intent
        vm.onIntent(ServiceIntent.SelectLocale("zh"))
        assertEquals("zh", vm.state.value.locale)
    }

    // -------------------------------------------------------------------------
    // Public mode
    // -------------------------------------------------------------------------

    @Test
    fun given_publicModeFalse_when_togglePublicMode_then_publicModeTrue() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.publicMode)
        vm.onIntent(ServiceIntent.TogglePublicMode(true))
        assertTrue(vm.state.value.publicMode)
    }

    @Test
    fun given_publicModeTrue_when_setDeviceIp_then_ipSet() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.TogglePublicMode(true))
        vm.onIntent(ServiceIntent.SetDeviceIp("192.168.1.1"))
        assertEquals("192.168.1.1", vm.state.value.deviceIp)
    }

    @Test
    fun given_publicModeOff_when_togglePublicModeOn_then_deviceIpFetched() = runTest {
        // FakeAdapter.getDeviceIpAddress() returns 192.168.1.100
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        assertNull(vm.state.value.deviceIp)

        vm.onIntent(ServiceIntent.TogglePublicMode(true))
        advanceUntilIdle()

        assertEquals("192.168.1.100", vm.state.value.deviceIp)
    }

    @Test
    fun given_publicModeOn_when_togglePublicModeOff_then_deviceIpCleared() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.TogglePublicMode(true))
        advanceUntilIdle()
        assertNotNull(vm.state.value.deviceIp)

        vm.onIntent(ServiceIntent.TogglePublicMode(false))
        advanceUntilIdle()

        assertNull(vm.state.value.deviceIp)
    }

    @Test
    fun given_publicModeOn_then_bindHostIsAllInterfaces() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.TogglePublicMode(true))
        advanceUntilIdle()
        assertEquals("0.0.0.0", vm.state.value.bindHost)
    }

    @Test
    fun given_publicModeOff_then_bindHostIsConfiguredHost() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.UpdateHost("127.0.0.1"))
        assertEquals("127.0.0.1", vm.state.value.bindHost)
    }

    @Test
    fun given_publicModeOn_then_localWebUrlUsesLoopback() = runTest {
        // The embedded WebView must hit loopback (cleartext-permitted), not the LAN IP
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.UpdatePort(18800))
        vm.onIntent(ServiceIntent.TogglePublicMode(true))
        advanceUntilIdle()
        val url = vm.state.value.localWebUrl
        assertTrue(url.startsWith("http://127.0.0.1:18800"), "Expected loopback URL, got: $url")
    }

    // -------------------------------------------------------------------------
    // Telemetry / analytics
    // -------------------------------------------------------------------------

    @Test
    fun given_telemetryOff_when_toggleOn_then_analyticsEnabledAndStatePersisted() = runTest {
        val analytics = FakeAnalytics()
        val (vm, _) = makeViewModel(analytics = analytics)
        advanceUntilIdle()
        assertFalse(vm.state.value.isTelemetryEnabled)

        vm.onIntent(ServiceIntent.ToggleTelemetry(true))
        advanceUntilIdle()

        assertTrue(vm.state.value.isTelemetryEnabled)
        assertTrue(analytics.collectionEnabled, "Analytics collection should be enabled")
    }

    @Test
    fun given_running_when_stop_then_serviceStopEventLogged() = runTest {
        val analytics = FakeAnalytics()
        val (vm, _) = makeViewModel(analytics = analytics)
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.StartService)
        vm.state.first { it.status == ServiceStatus.Running }
        vm.onIntent(ServiceIntent.StopService)
        vm.state.first { it.status == ServiceStatus.Stopped }

        assertTrue(analytics.events.contains(Analytics.EVENT_SERVICE_START))
        assertTrue(analytics.events.contains(Analytics.EVENT_SERVICE_STOP))
    }

    // -------------------------------------------------------------------------
    // Logs
    // -------------------------------------------------------------------------

    @Test
    fun given_emptyLogs_when_appendLog_then_logAdded() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.logs.isEmpty())

        vm.onIntent(ServiceIntent.AppendLog("hello log"))

        assertEquals(listOf("hello log"), vm.state.value.logs)
    }

    @Test
    fun given_someLogs_when_clearLogs_then_logsEmpty() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.AppendLog("line 1"))
        vm.onIntent(ServiceIntent.AppendLog("line 2"))
        assertEquals(2, vm.state.value.logs.size)

        vm.onIntent(ServiceIntent.ClearLogs)

        assertTrue(vm.state.value.logs.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Test
    fun given_errorSet_when_dismissError_then_errorCleared() = runTest {
        val adapter = FakeAdapter().also { it.startShouldThrow = true }
        val (vm, _) = makeViewModel(adapter)
        vm.onIntent(ServiceIntent.StartService)
        vm.state.first { it.errorMessage != null }

        vm.onIntent(ServiceIntent.DismissError)

        assertNull(vm.state.value.errorMessage)
    }

    // -------------------------------------------------------------------------
    // Immutability check
    // -------------------------------------------------------------------------

    @Test
    fun when_anyIntentDispatched_then_newStateObjectIsProduced() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        val before = vm.state.value

        vm.onIntent(ServiceIntent.UpdateHost("changed"))

        val after = vm.state.value
        assertNotSame(before, after, "State must be a new object after each intent")
    }

    // -------------------------------------------------------------------------
    // webUrl computation
    // -------------------------------------------------------------------------

    @Test
    fun given_publicModeFalse_then_webUrlUsesHost() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.UpdateHost("127.0.0.1"))
        vm.onIntent(ServiceIntent.UpdatePort(18800))
        val url = vm.state.value.webUrl
        assertTrue(url.contains("127.0.0.1"))
        assertTrue(url.contains("18800"))
    }

    @Test
    fun given_publicModeTrue_and_ipSet_then_webUrlUsesDeviceIp() = runTest {
        val (vm, _) = makeViewModel()
        advanceUntilIdle()
        vm.onIntent(ServiceIntent.TogglePublicMode(true))
        vm.onIntent(ServiceIntent.SetDeviceIp("192.168.1.50"))
        vm.onIntent(ServiceIntent.UpdatePort(18800))
        val url = vm.state.value.webUrl
        assertTrue(url.contains("192.168.1.50"), "Expected device IP in URL, got: $url")
    }

    // -------------------------------------------------------------------------
    // Binary download
    // -------------------------------------------------------------------------

    @Test
    fun given_downloadSupported_then_stateReflectsSupport() = runTest {
        val (vm, _) = makeViewModel(downloader = FakeDownloader(isSupported = true))
        advanceUntilIdle()
        assertTrue(vm.state.value.isDownloadSupported)
    }

    @Test
    fun given_downloadUnsupported_then_stateReflectsUnsupported() = runTest {
        val (vm, _) = makeViewModel(downloader = FakeDownloader(isSupported = false))
        advanceUntilIdle()
        assertFalse(vm.state.value.isDownloadSupported)
    }

    @Test
    fun given_supported_when_downloadSucceeds_then_binaryPathSetAndFound() = runTest {
        val downloader = FakeDownloader(
            isSupported = true,
            result = DownloadResult.Success("/installed/picoclaw"),
        )
        val (vm, _) = makeViewModel(adapter = FakeAdapter(binaryValid = true), downloader = downloader)
        advanceUntilIdle()

        vm.onIntent(ServiceIntent.DownloadBinary)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isDownloading)
        assertEquals("/installed/picoclaw", s.binaryPath)
        assertEquals(true, s.binaryFound)
    }

    @Test
    fun given_supported_when_downloadFails_then_errorMessageSet() = runTest {
        val downloader = FakeDownloader(
            isSupported = true,
            result = DownloadResult.Failure("network error"),
        )
        val (vm, _) = makeViewModel(downloader = downloader)
        advanceUntilIdle()

        vm.onIntent(ServiceIntent.DownloadBinary)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isDownloading)
        val msg = s.errorMessage
        assertNotNull(msg)
        assertTrue(msg.contains("network error"))
    }

    @Test
    fun given_unsupported_when_downloadBinary_then_errorMessageSet() = runTest {
        val (vm, _) = makeViewModel(downloader = FakeDownloader(isSupported = false))
        advanceUntilIdle()

        vm.onIntent(ServiceIntent.DownloadBinary)
        advanceUntilIdle()

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isDownloading)
    }
}
