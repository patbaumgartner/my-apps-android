package com.patbaumgartner.android.myapps

import android.app.Application
import androidx.work.Configuration

class MyAppsApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setDefaultProcessName("com.patbaumgartner.android.myapps")
            .build()
}
