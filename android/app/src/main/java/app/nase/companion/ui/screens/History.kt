package app.nase.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nase.companion.recap.Recap
import app.nase.companion.recap.RecapShots
import app.nase.companion.tasks.TaskRecord
import app.nase.companion.tasks.TaskState
import app.nase.companion.ui.RecapShotsRow
import app.nase.companion.ui.ShotViewer
import app.nase.companion.ui.StepTimeline
import app.nase.companion.ui.TaskRow
import app.nase.companion.ui.UndoControl
import app.nase.companion.ui.design.Banner
import app.nase.companion.ui.design.ButtonTone
import app.nase.companion.ui.design.EmptyState
import app.nase.companion.ui.design.Haptic
import app.nase.companion.ui.design.OrbMood
import app.nase.companion.ui.design.NaseButton
import app.nase.companion.ui.design.NaseCard
import app.nase.companion.ui.design.NaseIconButton
import app.nase.companion.ui.design.NaseIcons
import app.nase.companion.ui.design.NasePage
import app.nase.companion.ui.design.NaseSheet
import app.nase.companion.ui.design.PresenceOrb
import app.nase.companion.ui.design.SectionHeader
import app.nase.companion.ui.design.Segmented
import app.nase.companion.ui.design.Skeleton
import app.nase.companion.ui.design.Tag
import app.nase.companion.ui.design.Tone
import app.nase.companion.ui.durationText
import app.nase.companion.ui.moodFor
import app.nase.companion.ui.shared
import app.nase.companion.ui.taskStateLabel
import app.nase.companion.ui.taskTone
import app.nase.companion.ui.theme.Nase
import app.nase.companion.ui.theme.Shapes
import app.nase.companion.ui.theme.Space
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HistoryFilter(val label: String) { ALL("All"), DONE("Done"), ATTENTION("Needs a look") }

@Composable
fun HistoryScreen(
    tasks: List<TaskRecord>,
    loading: Boolean,
    now: Long,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onAsk: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = tasks.filter { task ->
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.DONE -> task.state == TaskState.Done
            HistoryFilter.ATTENTION -> task.state == TaskState.Failed || task.state == TaskState.Stopped || task.state == TaskState.Expired
        }
    }
    NasePage(
        title = "History",
        subtitle = "What you asked, what Nase did, and how it went. Kept only on this phone.",
        onBack = onBack,
        actions = {
            if (tasks.isNotEmpty()) NaseIconButton(NaseIcons.Trash, "Clear history", { confirmClear = true })
        },
        overlay = {
            NaseSheet(
                visible = confirmClear,
                onDismiss = { confirmClear = false },
                title = "Clear all history?",
                subtitle = "This deletes every task and screenshot on this phone. It can't be undone.",
            ) {
                NaseButton("Clear history", { confirmClear = false; onClear() }, tone = ButtonTone.Danger, icon = NaseIcons.Trash, haptic = Haptic.REJECT)
                NaseButton("Keep it", { confirmClear = false }, tone = ButtonTone.Secondary, haptic = Haptic.TAP)
            }
        },
    ) {
        Segmented(HistoryFilter.entries.map { it to it.label }, filter, { filter = it })
        when {
            loading -> repeat(4) { Skeleton(Modifier.fillMaxWidth().height(76.dp), shape = Shapes.card) }
            shown.isEmpty() -> EmptyState(
                title = if (tasks.isEmpty()) "No tasks yet" else "Nothing here",
                body = if (tasks.isEmpty()) "When you ask Nase for something, each step and the result land here." else "Try another filter.",
                action = if (tasks.isEmpty()) app.nase.companion.ui.design.BannerAction("Ask Nase", onAsk, icon = NaseIcons.Sparkles) else null,
            )
            else -> {
                var index = 0
                shown.groupBy { dayLabel(it.createdAt, now) }.forEach { (day, group) ->
                    SectionHeader(day)
                    group.forEach { task ->
                        TaskRow(task, now, onClick = { onOpen(task.id) }, index = index++)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    task: TaskRecord?,
    now: Long,
    shotFile: (String, String) -> File?,
    onBack: () -> Unit,
    onAskAgain: (String) -> Unit,
    onDelete: (String) -> Unit,
    onStop: () -> Unit,
    recap: Recap? = null,
    recapShots: RecapShots.Shots = RecapShots.Shots(null, null),
    onUndo: () -> Unit = {},
) {
    var viewing by remember { mutableStateOf<String?>(null) }
    NasePage(
        title = "Task",
        largeTitle = false,
        onBack = onBack,
        actions = {
            if (task != null && !task.running) NaseIconButton(NaseIcons.Trash, "Delete task", { onDelete(task.id) })
        },
        bottomBar = if (task == null) null else {
            {
                if (task.running) {
                    NaseButton("Stop", onStop, tone = ButtonTone.Danger, icon = NaseIcons.Stop, haptic = Haptic.STOP)
                } else if (task.resumable) {
                    NaseButton("Keep going", { onAskAgain(task.text) }, icon = NaseIcons.Play)
                } else {
                    NaseButton("Ask again", { onAskAgain(task.text) }, icon = NaseIcons.Refresh)
                }
            }
        },
        overlay = {
            val name = viewing
            if (name != null && task != null) ShotViewer(shotFile(task.id, name)) { viewing = null }
        },
    ) {
        if (task == null) {
            EmptyState("This task is gone", "It may have been cleared from history.", mood = OrbMood.Offline)
            return@NasePage
        }
        NaseCard(Modifier.shared("task-${task.id}", bounds = true), spacing = Space.lg) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PresenceOrb(moodFor(task.state), size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("You asked", style = Nase.type.overline, color = Nase.colors.inkFaint)
                    Text(timeLabel(task.createdAt, now), style = Nase.type.caption, color = Nase.colors.inkMuted)
                }
                Tag(taskStateLabel(task.state), taskTone(task.state))
            }
            Text("“${task.text}”", style = Nase.type.headline, color = Nase.colors.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Tag(task.brainLabel, Tone.Iris)
                Tag(sourceLabel(task.source), Tone.Neutral)
                Tag(durationText(task.durationMs(now)), Tone.Neutral)
                val shots = task.screenshots.size
                if (shots > 0) Tag(if (shots == 1) "1 screenshot" else "$shots screenshots", Tone.Neutral)
            }
        }
        task.outcome?.let { outcome ->
            Banner(
                title = if (task.state == TaskState.Done) "Result" else taskStateLabel(task.state),
                tone = when (task.state) {
                    TaskState.Done -> Tone.Mane
                    TaskState.Stopped -> Tone.Neutral
                    TaskState.Failed, TaskState.Expired -> Tone.Danger
                    else -> Tone.Iris
                },
                body = outcome,
                icon = when (task.state) {
                    TaskState.Done -> NaseIcons.CheckCircle
                    TaskState.Stopped -> NaseIcons.Stop
                    else -> NaseIcons.Info
                },
            )
        }
        // The before/after + undo recap, held in memory only for the latest task,
        // so this shows for the one Nase just finished and nothing older.
        if (recap != null && (recapShots.any || recap.hasUndo)) {
            SectionHeader("Recap")
            NaseCard(spacing = Space.md) {
                RecapShotsRow(recapShots)
                recap.undo?.let { UndoControl(it, onUndo) }
            }
        }
        SectionHeader("Steps")
        if (task.steps.isEmpty()) {
            Text(
                if (task.running) "Waiting for the first step…" else "No steps were needed.",
                style = Nase.type.bodySmall,
                color = Nase.colors.inkMuted,
                modifier = Modifier.padding(bottom = Space.lg),
            )
        } else {
            NaseCard {
                StepTimeline(task.steps, task.createdAt, task.running, shotFile = { name -> shotFile(task.id, name) }, onShot = { viewing = it })
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = Space.sm)) {
            Text(
                "Typed text and passwords aren't stored. Screenshots stay on this phone.",
                style = Nase.type.caption,
                color = Nase.colors.inkFaint,
            )
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "typed" -> "Typed"
    "voice", "button" -> "Spoken"
    "assistant" -> "Assistant gesture"
    "wake_word" -> "Hey Nase"
    else -> source.replaceFirstChar { it.uppercase() }
}

private fun dayLabel(at: Long, now: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = day.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val diff = today.get(Calendar.DAY_OF_YEAR) - day.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> "Today"
        sameYear && diff == 1 -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(at))
    }
}

private fun timeLabel(at: Long, now: Long): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))
    return "${dayLabel(at, now)} at $time"
}
