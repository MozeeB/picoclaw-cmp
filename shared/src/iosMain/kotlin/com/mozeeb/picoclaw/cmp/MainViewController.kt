package com.mozeeb.picoclaw.cmp

import androidx.compose.ui.window.ComposeUIViewController
import com.mozeeb.picoclaw.cmp.di.initKoin

private var koinStarted = false

fun MainViewController() = ComposeUIViewController {
    if (!koinStarted) {
        koinStarted = true
        initKoin()
    }
    App()
}
