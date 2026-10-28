package app.nase.companion.widget

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.nase.companion.R
import app.nase.companion.session.NaseSessionService
import app.nase.companion.session.SessionRepository
import app.nase.companion.voice.VoiceController
import app.nase.companion.voice.VoiceLaunch

/**
 * Quick Settings tile that starts a voice ask without opening the app. A tap
 * brings up listening (unlocking first if the phone is locked) and leaves the
 * shade. While a session or task is live the same tap is a kill switch: it
 * stops the running task and disconnects now, no unlock or app launch needed.
 */
class VoiceTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            val active = active()
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(if (active) R.string.tile_stop_label else R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        if (active()) {
            stopNow()
            return
        }
        val launch = { startVoice() }
        if (isLocked) unlockAndRun(launch) else launch()
    }

    /** A live or starting session, a running task, or an ask that is listening right now. */
    private fun active(): Boolean =
        SessionRepository.snapshot().busy || VoiceController.isRunning() || VoiceController.isListening()

    /**
     * Kill: stop the in-flight task and mic first — that is immediate and works
     * while locked — then end a live session so the assistant can't start
     * another. A one-off ask with no session keeps its pairing.
     */
    private fun stopNow() {
        VoiceController.stop()
        if (SessionRepository.snapshot().busy) {
            runCatching { NaseSessionService.stop(this) }
        }
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    private fun startVoice() {
        val pending = VoiceLaunch.pending(this, VoiceLaunch.SOURCE_TILE)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(VoiceLaunch.intent(this, VoiceLaunch.SOURCE_TILE))
        }
    }
}
