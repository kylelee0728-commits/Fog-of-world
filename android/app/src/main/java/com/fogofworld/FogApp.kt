package com.fogofworld

import android.app.Application
import com.fogofworld.data.FogStore

class FogApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FogStore.init(this)
    }
}
