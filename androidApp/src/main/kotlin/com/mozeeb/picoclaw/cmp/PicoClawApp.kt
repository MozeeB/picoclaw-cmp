package com.mozeeb.picoclaw.cmp

import android.app.Application
import com.mozeeb.picoclaw.cmp.di.initKoin

class PicoClawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(this)
    }
}
