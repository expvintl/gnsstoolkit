package com.gnsstoolkit

import android.app.Application
import com.gnsstoolkit.data.AppLanguage
import com.gnsstoolkit.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class GnssApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 语言必须在任何 Activity attach 之前定下来，否则会先按系统语言渲染、
        // 再由 Activity 自己 recreate 一次，冷启动能看到一闪。DataStore 首次
        // 读取是小文件，这里直接同步等；超时则退回跟随系统，不影响使用。
        AppLanguage.current = runBlocking {
            withTimeoutOrNull(500) {
                SettingsRepository(this@GnssApplication).currentLanguage()
            }
        } ?: AppLanguage.SYSTEM
    }
}
