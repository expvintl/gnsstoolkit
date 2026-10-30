package com.gnsstoolkit.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * 应用内语言覆盖。
 *
 * [current] 是本次进程正在生效的 BCP-47 标签，空串表示跟随系统。Activity 在
 * attachBaseContext 之前会读它来包装 Resources；设置里改语言时先写这个值再
 * recreate()，这样新 Activity 一创建就是目标语言，不会闪一下旧语言。
 */
object AppLanguage {

    /** 跟随系统。 */
    const val SYSTEM: String = ""

    /** 可选项：标签 -> 是否需要在设置页显示。 */
    val OPTIONS: List<String> = listOf(SYSTEM, "zh-CN", "en")

    @Volatile
    var current: String = SYSTEM

    /** 按 [current] 包装 base context；跟随系统时原样返回。 */
    fun wrap(base: Context): Context {
        val tag = current
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isEmpty()) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }
}
