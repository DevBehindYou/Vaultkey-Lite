package com.vaultkey.app

import android.app.Application
import com.vaultkey.core.VaultKeyGraph

class VaultKeyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Runs before any Activity/Service in this process, including the
        // keyboard and autofill service — see VaultKeyGraph's class doc.
        VaultKeyGraph.init(applicationContext)
    }
}
