package com.fogofworld.data

/**
 * 版本規則：年份尾數.月份[.小版本]，例如 26.8、26.8.1、26.9。
 * 編碼成 YYMMPP 之後才能比大小 —— 與 build.gradle.kts 的推導方式相同。
 */
object AppVersion {

    private val PATTERN = Regex("""^(\d{2})\.(\d{1,2})(?:\.(\d{1,2}))?$""")

    /** 版本字串轉成可比較的整數；格式不合回傳 null */
    fun codeOf(name: String): Int? {
        val m = PATTERN.find(name.trim().removePrefix("v")) ?: return null
        val (yy, mm, patch) = m.destructured
        val month = mm.toInt()
        if (month !in 1..12) return null
        return yy.toInt() * 10000 + month * 100 + (patch.ifEmpty { "0" }).toInt()
    }

    /** latest 是否比 current 新；任一方格式不明時保守回傳 false */
    fun isNewer(latest: String, current: String): Boolean {
        val l = codeOf(latest) ?: return false
        val c = codeOf(current) ?: return false
        return l > c
    }
}
