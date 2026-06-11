package com.qingmo.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.xiaomo.ModuleRegistry
import com.qingmo.app.xiaomo.modules.EmotionModule
import com.qingmo.app.xiaomo.modules.VoteModule
import com.qingmo.app.xiaomo.modules.SupportButtonModule
import com.qingmo.app.xiaomo.modules.ReactionPanelModule
import com.qingmo.app.xiaomo.modules.ChoicePanelModule
import com.qingmo.app.xiaomo.XiaoMoSettings

class QingmoApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
        XiaoMoSettings.init(this)
        ModuleRegistry.register(EmotionModule())
        ModuleRegistry.register(VoteModule())
        ModuleRegistry.register(SupportButtonModule())
        ModuleRegistry.register(ReactionPanelModule())
        ModuleRegistry.register(ChoicePanelModule())
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(ImageDecoderDecoder.Factory()) // API 28+
                add(GifDecoder.Factory())          // API 26-27 fallback
            }
            .build()
    }
}
