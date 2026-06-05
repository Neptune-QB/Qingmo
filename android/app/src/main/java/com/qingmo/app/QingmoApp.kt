package com.qingmo.app

import android.app.Application
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.xiaomo.ModuleRegistry
import com.qingmo.app.xiaomo.modules.EmotionModule
import com.qingmo.app.xiaomo.modules.QuizModule
import com.qingmo.app.xiaomo.modules.VoteModule

class QingmoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
        ModuleRegistry.register(EmotionModule())
        ModuleRegistry.register(VoteModule())
        ModuleRegistry.register(QuizModule())
    }
}
