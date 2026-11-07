package app.mannybrowser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
            // In-app update: one silent check per process start; offline or no manifest shows nothing.
            androidx.compose.runtime.LaunchedEffect(Unit) { state.checkForUpdateOnce() }
            val updateLifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(updateLifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) state.onResumeAfterGrant()
                }
                updateLifecycleOwner.lifecycle.addObserver(observer)
                onDispose { updateLifecycleOwner.lifecycle.removeObserver(observer) }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::state.isInitialized && state.fullscreen && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) state.fullscreen = false
            return true
        }
        return super.dispatchKeyEvent(event)
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
