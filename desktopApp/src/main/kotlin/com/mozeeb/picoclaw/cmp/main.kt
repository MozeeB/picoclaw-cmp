package com.mozeeb.picoclaw.cmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mozeeb.picoclaw.cmp.di.initKoin

fun main() = application {
    // Initialize Koin DI (DataStore + CoreServiceAdapter + ViewModel)
    initKoin()

    Window(
        onCloseRequest = ::exitApplication,
        title = "PicoClaw",
    ) {
        App()
    }
}
