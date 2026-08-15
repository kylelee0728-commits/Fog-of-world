package com.fogofworld.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 從 GitHub Releases 檢查、下載並安裝新版本。
 * 沒有用到任何額外套件，純 HttpURLConnection + org.json。
 */
object UpdateChecker {

    private const val LATEST_API =
        "https://api.github.com/repos/kylelee0728-commits/Fog-of-world/releases/latest"

    private const val TIMEOUT_MS = 15_000

    data class Release(
        val version: String,
        val apkUrl: String,
        val sizeBytes: Long,
        val notes: String,
    )

    sealed interface Result {
        data class Available(val release: Release) : Result
        data object UpToDate : Result
        data class Failed(val reason: String) : Result
    }

    /** 查最新版；currentVersion 通常傳 BuildConfig.VERSION_NAME */
    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FogOfWorld-Android")
            }
            val body = conn.use { it.inputStream.bufferedReader().readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets")

            var apkUrl: String? = null
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
            }

            when {
                tag.isEmpty() || apkUrl.isNullOrEmpty() ->
                    Result.Failed("最新版本沒有附上 APK")

                AppVersion.isNewer(tag, currentVersion) ->
                    Result.Available(Release(tag, apkUrl, size, json.optString("body")))

                else -> Result.UpToDate
            }
        }.getOrElse { e ->
            Result.Failed(e.message ?: "連不上 GitHub")
        }
    }

    /**
     * 下載 APK 到 App 快取目錄。
     * @param onProgress 0f..1f，總長度未知時回傳 -1f
     */
    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply {
                mkdirs()
                // 只留這次要裝的，舊的清掉
                listFiles()?.forEach { it.delete() }
            }
            val out = File(dir, "fog-of-world-${release.version}.apk")
            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FogOfWorld-Android")
            }
            conn.use { c ->
                val total = if (release.sizeBytes > 0) release.sizeBytes else c.contentLength.toLong()
                c.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                        }
                    }
                }
            }
            if (out.length() <= 0L) null else out
        }.getOrNull()
    }

    /** Android 8 以上要先讓使用者允許本 App 安裝不明來源應用程式 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 交給系統的安裝程式。簽章相同才會是「更新」而不是要求先移除 */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)
}

private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
    try {
        block(this)
    } finally {
        disconnect()
    }
