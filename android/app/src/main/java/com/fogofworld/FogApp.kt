package com.fogofworld

import android.app.Application
import android.content.Context
import com.fogofworld.data.FogStore
import com.fogofworld.data.TileCache
import org.osmdroid.config.Configuration
import java.io.File

class FogApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // osmdroid 需要 User-Agent，否則 OSM 會擋掉圖磚請求；
        // 快取放在 App 私有目錄，不需要儲存空間權限。
        val conf = Configuration.getInstance()
        conf.load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        conf.userAgentValue = "Lightfarer/${BuildConfig.VERSION_NAME} (Android)"
        val base = File(filesDir, "osmdroid").apply { mkdirs() }
        TileCache.configure(this, base)

        FogStore.init(this)
    }
}
