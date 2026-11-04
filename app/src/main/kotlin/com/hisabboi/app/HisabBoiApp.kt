package com.hisabboi.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale

@HiltAndroidApp
class HisabBoiApp : Application() {

    override fun attachBaseContext(base: android.content.Context) {
        val locale = Locale("bn", "BD")
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
}
