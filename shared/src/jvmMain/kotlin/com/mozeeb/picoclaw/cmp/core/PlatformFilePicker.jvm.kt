package com.mozeeb.picoclaw.cmp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop (JVM) file picker — opens a native JFileChooser dialog.
 * Mirrors Flutter's `FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: ['exe','sh'])`.
 */
actual suspend fun pickBinaryFile(): String? = withContext(Dispatchers.Main) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select PicoClaw Binary"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = false

        val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
        if (isWindows) {
            fileFilter = FileNameExtensionFilter("Executable (*.exe)", "exe")
        } else {
            // macOS / Linux — show all files (binary has no extension)
            isAcceptAllFileFilterUsed = true
        }
    }

    val result = chooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}
