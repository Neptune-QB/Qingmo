package com.qingmo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.qingmo.app.ui.navigation.NavGraph
import com.qingmo.app.ui.theme.QingmoTheme
import com.qingmo.app.xiaomo.ModuleRegistry
import com.qingmo.app.xiaomo.modules.EmotionModule
import com.qingmo.app.xiaomo.modules.VoteModule
import com.qingmo.app.xiaomo.modules.QuizModule
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.data.user.DeviceIdProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 初始化 Token / 设备 ID
        TokenManager.init(this)
        val deviceId = DeviceIdProvider.getDeviceId(this)
        // 注册小墨互动模块
        ModuleRegistry.register(EmotionModule())
        ModuleRegistry.register(VoteModule())
        ModuleRegistry.register(QuizModule())
        setContent {
            QingmoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NavGraph(deviceId = deviceId)
                }
            }
        }
    }
}
