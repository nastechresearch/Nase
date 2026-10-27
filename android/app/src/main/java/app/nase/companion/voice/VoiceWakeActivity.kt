package app.nase.companion.voice

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.nase.companion.overlay.AssistantPanel
import app.nase.companion.ui.theme.NaseTheme

/** "Hey Nase" over the lock screen. It turns the screen on but never dismisses the keyguard. */
class VoiceWakeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        val openedAt = System.currentTimeMillis()
        setContent {
            NaseTheme(dark = true) {
                AssistantPanel(onClose = { finish() }, openedAt = openedAt)
            }
        }
        val start = intent.getBooleanExtra(EXTRA_START, true)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "wake_word"
        if (start && !VoiceController.isListening()) VoiceController.listen(this, source)
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_START = "start"
    }
}
