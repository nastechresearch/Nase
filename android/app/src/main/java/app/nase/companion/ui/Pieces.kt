package app.nase.companion.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nase.companion.tasks.StepKind
import app.nase.companion.tasks.TaskRecord
import app.nase.companion.tasks.TaskState
import app.nase.companion.tasks.TaskStep
import app.nase.companion.ui.design.ButtonTone
import app.nase.companion.ui.design.Haptic
import app.nase.companion.ui.design.IconTile
import app.nase.companion.ui.design.IconTone
import app.nase.companion.ui.design.OrbMood
import app.nase.companion.ui.design.NaseButton
import app.nase.companion.ui.design.NaseCard
import app.nase.companion.ui.design.NaseIconButton
import app.nase.companion.ui.design.NaseIcons
import app.nase.companion.ui.design.PresenceOrb
import app.nase.companion.ui.design.Tag
import app.nase.companion.ui.design.Tone
import app.nase.companion.ui.design.enter
import app.nase.companion.ui.design.pressable
import app.nase.companion.ui.design.safeInsets
import app.nase.companion.ui.design.toneColors
import app.nase.companion.ui.theme.Motion
import app.nase.companion.ui.theme.Nase
import app.nase.companion.ui.theme.Shapes
import app.nase.companion.ui.theme.Space
import app.nase.companion.ui.theme.SquircleShape
import java.io.File

fun iconFor(icon: SuggestionIcon): ImageVector = when (icon) {
    SuggestionIcon.Car -> NaseIcons.Car
    SuggestionIcon.Bag -> NaseIcons.ShoppingBag
    SuggestionIcon.Bed -> NaseIcons.Bed
    SuggestionIcon.Utensils -> NaseIcons.Utensils
    SuggestionIcon.Photos -> NaseIcons.Image
    SuggestionIcon.Package -> NaseIcons.Package
    SuggestionIcon.Home -> NaseIcons.Home
    SuggestionIcon.Plane -> NaseIcons.Plane
    SuggestionIcon.Pin -> NaseIcons.MapPin
    SuggestionIcon.Mail -> NaseIcons.Mail
    SuggestionIcon.Bolt -> NaseIcons.Bolt
    SuggestionIcon.Music -> NaseIcons.Music
    SuggestionIcon.Play -> NaseIcons.Play
}

/**
 * A fresh set of suggestion chips for a screen, filtered to apps actually installed on
 * this phone. Remembered per screen instance so it stays put while you read it but
 * reshuffles the next time the surface opens.
 */
@Composable
fun rememberSuggestions(count: Int = 4): List<Suggestion> {
    val context = LocalContext.current
    return remember {
        val pm = context.packageManager
        pickSuggestions(
            isInstalled = { pkg ->
                try {
                    pm.getLaunchIntentForPackage(pkg) != null
                } catch (_: Exception) {
                    false
                }
            },
            count = count,
        )
    }
}

fun iconFor(kind: StepKind): ImageVector = when (kind) {
    StepKind.Open -> NaseIcons.Layers
    StepKind.Tap -> NaseIcons.Tap
    StepKind.Type -> NaseIcons.Type
    StepKind.Swipe -> NaseIcons.Swipe
    StepKind.Key -> NaseIcons.Back
    StepKind.Look -> NaseIcons.Eye
    StepKind.Read -> NaseIcons.Search
    StepKind.Speak -> NaseIcons.Speak
    StepKind.Ask -> NaseIcons.Ask
    StepKind.Confirm -> NaseIcons.Shield
    StepKind.Wait -> NaseIcons.Clock
    StepKind.Status -> NaseIcons.Sparkles
    StepKind.Result -> NaseIcons.CheckCircle
    StepKind.Error -> NaseIcons.AlertCircle
}

fun iconFor(state: TaskState): ImageVector = when (state) {
    TaskState.Done -> NaseIcons.CheckCircle
    TaskState.Stopped -> NaseIcons.Stop
    TaskState.Failed, TaskState.Expired -> NaseIcons.AlertCircle
    TaskState.Waiting, TaskState.Queued -> NaseIcons.Clock
    else -> NaseIcons.Activity
}

fun moodFor(state: TaskState): OrbMood = when (state) {
    TaskState.Done -> OrbMood.Success
    TaskState.Failed, TaskState.Expired -> OrbMood.Error
    TaskState.Stopped -> OrbMood.Idle
    TaskState.Waiting, TaskState.Queued -> OrbMood.Waiting
    TaskState.Sent -> OrbMood.Thinking
    TaskState.Running -> OrbMood.Working
}

/** A soft pool of iris light behind a screen's hero. */
fun Modifier.ambientGlow(color: Color, center: Float = 0.18f, radius: Float = 0.9f): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.55f),
            0.5f to color.copy(alpha = 0.18f),
            1f to Color.Transparent,
            center = Offset(size.width / 2f, size.height * center),
            radius = size.width * radius,
        ),
    )
}

@Composable
fun TaskRow(task: TaskRecord, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier, index: Int = 0) {
    val colors = Nase.colors
    val meta = buildList {
        add(task.brainLabel)
        if (task.actionCount > 0) add("${task.actionCount} ${if (task.actionCount == 1) "step" else "steps"}")
        add(relativeTime(task.createdAt, now))
    }.joinToString(" · ")
    NaseCard(
        modifier = modifier.enter(index, task.id).shared("task-${task.id}", bounds = true),
        onClick = onClick,
        onClickLabel = "Open task",
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.lg, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(iconFor(task.state), taskTone(task.state))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.text, style = Nase.type.bodyStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, style = Nase.type.caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(NaseIcons.ChevronRight, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(18.dp))
        }
    }
}

/** Steps as a vertical timeline. New steps glide in; screenshots open full screen. */
@Composable
fun StepTimeline(
    steps: List<TaskStep>,
    startedAt: Long,
    running: Boolean,
    modifier: Modifier = Modifier,
    shotFile: (String) -> File? = { null },
    onShot: (String) -> Unit = {},
) {
    val colors = Nase.colors
    Column(modifier.fillMaxWidth().animateContentSize(Motion.glide())) {
        steps.forEachIndexed { index, step ->
            val last = index == steps.lastIndex
            Row(Modifier.fillMaxWidth().enter(0, "${step.at}-$index"), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val tone = when {
                        !step.ok -> Tone.Warning
                        step.kind == StepKind.Result -> Tone.Success
                        step.kind == StepKind.Status -> Tone.Iris
                        else -> Tone.Neutral
                    }
                    val tc = toneColors(tone)
                    Box(
                        Modifier.size(30.dp).clip(SquircleShape(10.dp)).background(tc.soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(iconFor(step.kind), contentDescription = null, tint = if (step.ok) tc.fg else colors.warning, modifier = Modifier.size(15.dp))
                    }
                    if (!last || running) {
                        Box(
                            Modifier
                                .padding(vertical = 2.dp)
                                .width(2.dp)
                                .height(if (step.shot != null) 150.dp else 20.dp)
                                .background(colors.hairlineStrong),
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(top = 5.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (step.count > 1) "${step.label} ×${step.count}" else step.label,
                            style = Nase.type.bodySmall,
                            color = if (step.ok) colors.ink else colors.warning,
                            modifier = Modifier.weight(1f),
                        )
                        Text("+${elapsed(step.at - startedAt)}", style = Nase.type.caption, color = colors.inkFaint)
                    }
                    val shot = step.shot
                    if (shot != null) {
                        ShotThumb(shotFile(shot), onClick = { onShot(shot) })
                    }
                }
            }
        }
        if (running) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { PresenceOrb(OrbMood.Working, size = 30.dp) }
                Text("Working…", style = Nase.type.bodySmall, color = colors.inkMuted)
            }
        }
    }
}

private fun elapsed(ms: Long): String {
    val s = (ms / 100) / 10.0
    return if (s < 60) "${"%.1f".format(s)}s" else "${(s / 60).toInt()}m"
}

@Composable
fun rememberShot(file: File?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = file?.takeIf { it.exists() }?.let { f ->
            runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}

@Composable
fun ShotThumb(file: File?, onClick: () -> Unit, height: Dp = 140.dp) {
    val colors = Nase.colors
    val image = rememberShot(file)
    Box(
        Modifier
            .height(height)
            .width(height * 0.52f)
            .clip(Shapes.tile)
            .background(colors.surfaceHigh)
            .border(1.dp, colors.hairlineStrong, Shapes.tile)
            .pressable(onClick = onClick, label = "Open screenshot", pressedScale = 0.96f)
            .semantics { contentDescription = "Screenshot from this step" },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(NaseIcons.Image, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun ShotViewer(file: File?, onClose: () -> Unit) {
    val image = rememberShot(file)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .pressable(onClick = onClose, label = "Close screenshot", pressedScale = 1f, haptic = Haptic.NONE)
            .windowInsetsPadding(safeInsets())
            .padding(Space.xl),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = "Screenshot", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clip(Shapes.card))
        }
        NaseIconButton(NaseIcons.Close, "Close", onClose, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** Home's big entry point. Tapping the words opens Ask; the mic starts listening. */
@Composable
fun AskBar(onOpen: () -> Unit, onMic: () -> Unit, modifier: Modifier = Modifier, hint: String = "Ask Nase anything…") {
    val colors = Nase.colors
    val shape = SquircleShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .shared("askbar", bounds = true)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.iris.copy(alpha = if (colors.dark) 0.35f else 0.28f), shape)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 60.dp)
                .clip(SquircleShape(22.dp))
                .pressable(onClick = onOpen, label = "Ask Nase", pressedScale = 0.98f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NaseIcons.Sparkles, contentDescription = null, tint = colors.iris, modifier = Modifier.size(22.dp))
            Text(hint, style = Nase.type.body, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NaseIconButton(NaseIcons.Mic, "Talk to Nase", onMic, tone = IconTone.Iris, size = 48.dp, haptic = Haptic.LISTEN)
    }
}

/** The running task on Home: what Nase is doing now, with Stop in reach. */
@Composable
fun LiveTaskCard(task: TaskRecord, onStop: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Nase.colors
    NaseCard(modifier.shared("askbar", bounds = true), onClick = onOpen, onClickLabel = "Open task", border = colors.iris.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            PresenceOrb(moodFor(task.state), size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(task.brainLabel, style = Nase.type.caption, color = colors.iris)
                AnimatedContent(
                    targetState = task.headline ?: taskStateLabel(task.state),
                    transitionSpec = {
                        (slideInVertically(Motion.slide) { it / 2 } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(Motion.slide) { -it / 2 } + fadeOut(tween(120)))
                    },
                    label = "live-headline",
                ) { line -> Text(line, style = Nase.type.bodyStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Tag("${task.actionCount} ${if (task.actionCount == 1) "step" else "steps"}", Tone.Iris)
        }
        Text("“${task.text}”", style = Nase.type.bodySmall, color = colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        NaseButton("Stop", onStop, tone = ButtonTone.Danger, icon = NaseIcons.Stop, haptic = Haptic.STOP)
    }
}

@Composable
fun BrainChip(label: String, tone: Tone, onClick: () -> Unit, live: Boolean = false) {
    val colors = Nase.colors
    val tc = toneColors(tone)
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(Shapes.chip)
            .pressable(onClick = onClick, label = "Choose a brain", pressedScale = 0.95f, haptic = Haptic.SELECT)
            .background(colors.surfaceHigh)
            .border(1.dp, colors.hairlineStrong, Shapes.chip)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(SquircleShape(4.dp, 0f)).background(if (live) tc.fg else colors.control))
        Text(label, style = Nase.type.label, color = colors.ink, maxLines = 1)
        Icon(NaseIcons.ChevronDown, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun CodeBlock(code: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Nase.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(if (colors.dark) Color(0xFF07060D) else colors.surfaceHigh)
            .border(1.dp, colors.hairlineStrong, Shapes.control)
            .padding(start = Space.lg, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text(code, style = Nase.type.mono, color = colors.ink, softWrap = false)
        }
        NaseIconButton(NaseIcons.Copy, "Copy command", onCopy, size = 40.dp, haptic = Haptic.CONFIRM)
    }
}

/** Numbered page dots for onboarding. The current page stretches into a pill. */
@Composable
fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    val colors = Nase.colors
    Row(modifier.semantics { contentDescription = "Step ${index + 1} of $count" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { dot ->
            val on = dot == index
            val width by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 7.dp, Motion.glide(), label = "dot")
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(SquircleShape(4.dp, 0f))
                    .background(if (on) colors.iris else if (dot < index) colors.iris.copy(alpha = 0.45f) else colors.hairlineStrong),
            )
        }
    }
}
