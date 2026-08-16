package com.langualens.app

import android.app.Application
import com.langualens.app.data.Repo
import com.langualens.app.translate.Translate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LanguaLensApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Reading the repo applies the saved language pair to the translator.
        Repo.get(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (Translate.isReady()) Translate.prepare()
        }
    }
}
