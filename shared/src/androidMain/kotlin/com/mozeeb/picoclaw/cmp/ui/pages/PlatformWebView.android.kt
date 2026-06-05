package com.mozeeb.picoclaw.cmp.ui.pages

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    private var retries = 0

                    // The picoclaw server may take a moment to bind the port after the
                    // process starts. Auto-retry the main frame a few times so the page
                    // loads once the server is up (instead of leaving ERR_CONNECTION_REFUSED).
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame && retries < MAX_RETRIES) {
                            retries++
                            view.postDelayed({ view.reload() }, RETRY_DELAY_MS)
                        }
                    }

                    override fun onPageFinished(view: WebView, finishedUrl: String?) {
                        retries = 0 // reset once a load succeeds
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(url)
            }
        },
        update = { webView -> webView.loadUrl(url) },
        modifier = modifier,
    )
}

private const val MAX_RETRIES = 8
private const val RETRY_DELAY_MS = 1500L
