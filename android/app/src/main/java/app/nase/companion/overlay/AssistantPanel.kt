package app.nase.companion.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nase.companion.tasks.TaskRecord
import app.nase.companion.tasks.TaskRuntime
import app.nase.companion.tasks.TaskState
import app.nase.companion.ui.design.ButtonTone
import app.nase.companion.ui.design.Haptic
import app.nase.companion.ui.design.OrbMood
import app.nase.companion.ui.design.NaseButton
import app.nase.companion.ui.design.NaseIcons
import app.nase.companion.ui.design.PresenceOrb
import app.nase.companion.ui.design.pressable
import app.nase.companion.ui.design.safeInsets
import app.nase.companion.ui.theme.Nase
import app.nase.companion.ui.theme.SquircleShape
import app.nase.companion.voice.NoticeAction
import app.nase.companion.voice.VoiceController
import app.nase.companion.voice.VoiceUi
import kotlinx.coroutines.delay

/**
 * What the assistant gesture and "Hey Nase" show: a card at the bottom of
 * the screen. It closes on its own a moment after the result, on Stop, on
 * Back, or when the owner taps outside it.
 */
@Composable
fun AssistantPanel(onClose: () -> Unit, openedAt: Long = System.currentTimeMillis()) {
    val voice by VoiceController.voice.collectAsState()
    val task by TaskRuntime.live.collectAsState()
    val relevant = task?.takeIf { it.createdAt >= openedAt - 1_000 }
    val idle = !voice.listening && voice.prompt == null && (relevant == null || !relevant.running)
    LaunchedEffect(idle, relevant?.state, voice.notice) {
        if (idle) {
            delay(if (relevant != null) 2_800 else if (voice.notice != null) 5_500 else 2_200)
            onClose()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Nase.colors.scrim)
            .pressable(onClick = onClose, label = "Close", pressedScale = 1f, haptic = Haptic.NONE),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(safeInsets())
                .padding(12.dp)
                .clip(SquircleShape(34.dp))
                .background(Nase.colors.surface)
                .pressable(onClick = {}, label = null, pressedScale = 1f, haptic = Haptic.NONE)
                .padding(24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PresenceOrb(mood = moodFor(voice, relevant), level = voice.level, size = 132.dp)
            AnimatedContent(
                targetState = headline(voice, relevant),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "assistant-line",
            ) { line ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(line.first, style = Nase.type.headline, color = Nase.colors.ink, textAlign = TextAlign.Center)
                    if (line.second.isNotBlank()) {
                        Text(line.second, style = Nase.type.body, color = Nase.colors.inkMuted, textAlign = TextAlign.Center)
                    }
                }
            }
            val prompt = voice.prompt
            when {
                prompt != null && prompt.confirm -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NaseButton("No", { VoiceController.answer(false) }, tone = ButtonTone.Secondary, modifier = Modifier.weight(1f))
                    NaseButton("Yes", { VoiceController.answer(true) }, modifier = Modifier.weight(1f))
                }
                voice.notice?.action == NoticeAction.ALLOW_MIC ->
                    NaseButton("Allow microphone", { VoiceController.openNaseFor(NoticeAction.ALLOW_MIC); onClose() }, icon = NaseIcons.Mic)
                relevant != null && relevant.running ->
                    NaseButton("Stop", { VoiceController.stop(); onClose() }, tone = ButtonTone.Danger, icon = NaseIcons.Stop, haptic = Haptic.STOP)
                voice.listening ->
                    NaseButton("Cancel", { VoiceController.cancelListening(); onClose() }, tone = ButtonTone.Secondary)
                else -> NaseButton("Close", onClose, tone = ButtonTone.Secondary)
            }
        }
    }
}

private fun moodFor(voice: VoiceUi, task: TaskRecord?): OrbMood = when {
    voice.listening -> OrbMood.Listening
    voice.prompt != null -> OrbMood.Waiting
    voice.notice != null -> OrbMood.Offline
    task == null -> OrbMood.Idle
    task.state == TaskState.Done -> OrbMood.Success
    task.state == TaskState.Failed -> OrbMood.Error
    task.state == TaskState.Waiting || task.state == TaskState.Queued -> OrbMood.Waiting
    task.running -> OrbMood.Working
    else -> OrbMood.Idle
}

private fun headline(voice: VoiceUi, task: TaskRecord?): Pair<String, String> {
    voice.prompt?.let { return it.text to (if (it.confirm) "Say yes or no, or tap below." else "Nase is listening for your answer.") }
    voice.notice?.let { return it.title to it.body }
    if (voice.listening) return (if (voice.transcript.isBlank()) "Listening…" else "“${voice.transcript}”") to "Say stop to cancel."
    if (task != null) {
        return when {
            task.running -> "“${task.text}”" to (task.headline ?: "Nase is on it")
            task.state == TaskState.Done -> "Done" to (task.outcome ?: "")
            task.state == TaskState.Stopped -> "Stopped" to "You can pick it back up from Nase."
            else -> "Couldn't finish" to (task.outcome ?: "")
        }
    }
    return "Hi" to "What should Nase do?"
}
