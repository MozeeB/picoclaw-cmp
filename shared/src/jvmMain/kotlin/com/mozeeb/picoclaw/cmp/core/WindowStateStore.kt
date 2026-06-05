package com.mozeeb.picoclaw.cmp.core

/**
 * Persists the desktop window's size and position via [AppSettings] (DataStore),
 * so the window reopens where the user left it.
 *
 * Sizes/positions are stored in dp (Int). A width of [UNSET] means "use default / center".
 */
class WindowStateStore(private val settings: AppSettings) {

    companion object {
        const val UNSET = -1
        private const val KEY_W = "window_width"
        private const val KEY_H = "window_height"
        private const val KEY_X = "window_x"
        private const val KEY_Y = "window_y"
        const val DEFAULT_WIDTH = 1000
        const val DEFAULT_HEIGHT = 720
    }

    data class Bounds(
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int,
    ) {
        /** True when a saved position exists (else the window should be centered). */
        val hasPosition: Boolean get() = x != UNSET && y != UNSET
    }

    suspend fun load(): Bounds = Bounds(
        width = settings.getInt(KEY_W, DEFAULT_WIDTH).coerceAtLeast(400),
        height = settings.getInt(KEY_H, DEFAULT_HEIGHT).coerceAtLeast(300),
        x = settings.getInt(KEY_X, UNSET),
        y = settings.getInt(KEY_Y, UNSET),
    )

    suspend fun save(width: Int, height: Int, x: Int, y: Int) {
        if (width > 0) settings.putInt(KEY_W, width)
        if (height > 0) settings.putInt(KEY_H, height)
        settings.putInt(KEY_X, x)
        settings.putInt(KEY_Y, y)
    }
}
