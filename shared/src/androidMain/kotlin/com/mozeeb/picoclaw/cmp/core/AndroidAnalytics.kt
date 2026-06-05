package com.mozeeb.picoclaw.cmp.core

import android.util.Log

/**
 * Android analytics stub — ready to be backed by Firebase Analytics.
 *
 * To enable Firebase later:
 *   1. Add the `com.google.gms:google-services` plugin + `firebase-analytics` dependency.
 *   2. Place `google-services.json` in `androidApp/`.
 *   3. Replace the `Log.d` calls below with `FirebaseAnalytics.getInstance(context)`:
 *        - setEnabled  → firebase.setAnalyticsCollectionEnabled(enabled)
 *        - logEvent    → firebase.logEvent(name, bundleOf(...))
 *
 * Until then this is a privacy-safe no-op that only logs locally when enabled.
 */
class AndroidAnalytics : Analytics {
    private var enabled = false

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Log.d(TAG, "analytics collection ${if (enabled) "enabled" else "disabled"}")
        // Firebase: FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        if (!enabled) return
        Log.d(TAG, "event: $name $params")
        // Firebase: FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
    }

    private companion object {
        const val TAG = "PicoClawAnalytics"
    }
}
