package app.mannybrowser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.mannybrowser.ui.BrowserScreen

class MainActivity : ComponentActivity() {
    private lateinit var state: BrowserState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        state = BrowserState(this)
        state.newTab()
        handleIntent(intent)
        setContent {
            MannyTheme(darkOverride = state.darkModeOverride) {
                BrowserScreen(state)
            }
            androidx.compose.runtime.LaunchedEffect(state.fullscreen) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                if (state.fullscreen) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { state.openInNewTab(it) }
        }
    }

    override fun onDestroy() {
        if (isFinishing) state.destroyAll()
        super.onDestroy()
    }
}
