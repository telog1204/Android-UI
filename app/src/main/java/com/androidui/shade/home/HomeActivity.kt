package com.androidui.shade.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.androidui.shade.ui.home.HomeScreen
import com.androidui.shade.ui.theme.ShadeTheme
import kotlinx.coroutines.flow.MutableSharedFlow

/** Android 15（Pixel）風のホーム画面 */
class HomeActivity : ComponentActivity() {

    private lateinit var repository: AppRepository

    /** ホーム画面の表示中にホームボタンが押されたことを伝える */
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 壁紙の上なので、ステータスバーとナビゲーションバーのアイコンは白にする
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        repository = AppRepository(applicationContext, lifecycleScope).also { it.start() }
        setContent {
            ShadeTheme {
                HomeScreen(repository = repository, homePressed = homePressed)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed.tryEmit(Unit)
    }

    override fun onDestroy() {
        repository.stop()
        super.onDestroy()
    }
}
