package com.mozeeb.picoclaw.cmp.core

/**
 * Platform file picker for selecting the picoclaw binary.
 *
 * Mirrors Flutter's `FilePicker.platform.pickFiles()` in ConfigPage.
 *
 * - Desktop (JVM): opens a native `JFileChooser` dialog
 * - Android: not available from shared module (handled by the UI via SAF)
 * - iOS / Web: not supported (returns null)
 *
 * Returns the absolute path of the selected file, or null if cancelled / unsupported.
 */
expect suspend fun pickBinaryFile(): String?
