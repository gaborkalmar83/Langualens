package com.lingualens.app

import android.app.Application
import com.lingualens.app.data.Repo
import com.lingualens.app.translate.Nl2En
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LinguaLensApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Repo.get(this)
        // Warm the on-device model up in the background so the first tap is instant.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (Nl2En.isModelDownloaded()) Nl2En.prepare()
        }
    }

    companion object {
        lateinit var instance: LinguaLensApp
            private set
    }
}
