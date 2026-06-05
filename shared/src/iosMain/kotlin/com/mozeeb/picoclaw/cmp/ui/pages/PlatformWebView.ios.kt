package com.mozeeb.picoclaw.cmp.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * iOS WebView — native WKWebView embedded via Compose [UIKitView].
 * Loads [url] and reloads whenever it changes.
 */
@Suppress("DEPRECATION") // UIKitView(factory, modifier, update) — stable across CMP 1.x
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    val webView = remember {
        WKWebView(
            frame = CGRectZero.readValue(),
            configuration = WKWebViewConfiguration(),
        )
    }

    UIKitView(
        factory = { webView },
        modifier = modifier,
        update = { view ->
            NSURL.URLWithString(url)?.let { nsUrl ->
                view.loadRequest(NSURLRequest(uRL = nsUrl))
            }
        },
    )
}
