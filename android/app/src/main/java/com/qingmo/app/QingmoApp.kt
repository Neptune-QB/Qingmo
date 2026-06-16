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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

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

        // 预下载：第一期第一集 + 第二期第一集 + 分支视频，其他走 ExoPlayer 缓存
        preloadVideo("videos/1/63.mp4", "1_63.mp4")
        preloadVideo("videos/2/1.mp4", "2_1.mp4")
        preloadVideo("videos/2/1-1.mp4", "branch_2_1.mp4")
    }

    private fun preloadVideo(videoPath: String, fileName: String) {
        val file = File(cacheDir, fileName)
        if (file.exists() && file.length() > 0) return
        val url = "${com.qingmo.app.data.api.RetrofitClient.BASE_URL}$videoPath"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val resp = client.newCall(Request.Builder().url(url).build()).execute()
                resp.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun getCachedVideo(context: Application, fileName: String): File? {
            val file = File(context.cacheDir, fileName)
            return if (file.exists() && file.length() > 0) file else null
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(ImageDecoderDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .build()
    }
}
