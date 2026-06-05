package com.mozeeb.picoclaw.cmp

// Re-export the main App composable from the ui package so existing entry points
// (MainActivity, main.kt, MainViewController) can call App() without changing their imports.
// The actual implementation lives in com.mozeeb.picoclaw.cmp.ui.App.
import androidx.compose.runtime.Composable

@Composable
fun App() = com.mozeeb.picoclaw.cmp.ui.App()
