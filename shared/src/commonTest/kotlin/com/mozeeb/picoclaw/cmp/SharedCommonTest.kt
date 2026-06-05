package com.mozeeb.picoclaw.cmp

import com.mozeeb.picoclaw.cmp.mvi.ServiceState
import com.mozeeb.picoclaw.cmp.mvi.ServiceStatus
import com.mozeeb.picoclaw.cmp.ui.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Basic sanity tests for the common module.
 */
class SharedCommonTest {

    @Test
    fun serviceState_defaultValues() {
        val state = ServiceState()
        assertEquals(ServiceStatus.Stopped, state.status)
        assertEquals("127.0.0.1", state.host)
        assertEquals(18800, state.port)
        assertEquals(AppThemeMode.Carbon, state.theme)
        assertEquals("en", state.locale)
    }

    @Test
    fun serviceState_webUrl_localMode() {
        val state = ServiceState(host = "127.0.0.1", port = 18800, publicMode = false)
        assertTrue(state.webUrl.startsWith("http://127.0.0.1:18800"))
    }

    @Test
    fun serviceState_webUrl_publicMode() {
        val state = ServiceState(
            host = "127.0.0.1",
            port = 18800,
            publicMode = true,
            deviceIp = "192.168.1.5",
        )
        assertTrue(state.webUrl.contains("192.168.1.5"), "Public mode should use device IP")
    }

    @Test
    fun serviceState_copyProducesNewObject() {
        val original = ServiceState()
        val copy = original.copy(host = "10.0.0.1")
        assertNotSame(original, copy)
        assertEquals("10.0.0.1", copy.host)
        assertEquals("127.0.0.1", original.host, "Original must not be mutated")
    }
}
