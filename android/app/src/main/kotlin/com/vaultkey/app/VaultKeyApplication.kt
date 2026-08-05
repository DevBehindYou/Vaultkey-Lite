package com.vaultkey.app

import android.app.Application
import com.vaultkey.core.VaultKeyGraph

class VaultKeyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VaultKeyGraph.init(applicationContext)
    }
}
