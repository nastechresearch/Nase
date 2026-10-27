package app.nase.companion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.nase.companion.session.ReadinessState
import app.nase.companion.tasks.TaskRecord
import app.nase.companion.tasks.TaskState
import app.nase.companion.ui.PageDots
import app.nase.companion.ui.SetupItem
import app.nase.companion.ui.StepTimeline
import app.nase.companion.ui.ambientGlow
import app.nase.companion.ui.design.Banner
import app.nase.companion.ui.design.BannerAction
import app.nase.companion.ui.design.BrandMark
import app.nase.companion.ui.design.ButtonTone
import app.nase.companion.ui.design.ConsentRow
import app.nase.companion.ui.design.Haptic
import app.nase.companion.ui.design.IconTile
import app.nase.companion.ui.design.OrbMood
import app.nase.companion.ui.design.NaseButton
import app.nase.companion.ui.design.NaseCard
import app.nase.companion.ui.design.NaseIcons
import app.nase.companion.ui.design.NasePage
import app.nase.companion.ui.design.PresenceOrb
import app.nase.companion.ui.design.ProgressRing
import app.nase.companion.ui.design.StatusBarBand
import app.nase.companion.ui.design.Tag
import app.nase.companion.ui.design.TextAction
import app.nase.companion.ui.design.Tone
import app.nase.companion.ui.design.enter
import app.nase.companion.ui.design.rememberHaptics
import app.nase.companion.ui.design.safeBottom
import app.nase.companion.ui.design.safeHorizontal
import app.nase.companion.ui.moodFor
import app.nase.companion.ui.shared
import app.nase.companion.ui.theme.Nase
import app.nase.companion.ui.theme.Space

@Composable
fun WelcomeScreen(pendingLink: Boolean, onStart: () -> Unit) {
    val colors = Nase.colors
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarBand(colors.canvas)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .ambientGlow(colors.canvasGlow, center = 0.34f, radius = 0.95f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(safeHorizontal())
                    .padding(horizontal = Space.gutter),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 34.dp)
                    Text("Nase", style = Nase.type.title.copy(fontFamily = app.nase.companion.ui.theme.Serif), color = colors.ink, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    PageDots(4, 0)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = Space.section), contentAlignment = Alignment.Center) {
                    PresenceOrb(OrbMood.Idle, size = 250.dp, modifier = Modifier.enter(0).shared("orb"), description = "Nase")
                }
                Text(
                    buildAnnotatedString {
                        append("Your phone,\nwith a ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.iris)) { append("companion") }
                        append(".")
                    },
                    style = Nase.type.hero,
                    color = colors.ink,
                    modifier = Modifier.enter(1).semantics { heading() },
                )
                Spacer(Modifier.height(Space.lg))
                Text(
                    "Nase lets Grok Bot see your screen and tap, type, and open apps for you. You watch, and you can stop it at any time.",
                    style = Nase.type.body,
                    color = colors.inkMuted,
                    modifier = Modifier.enter(2),
                )
                Spacer(Modifier.height(Space.xl))
                Row(Modifier.enter(3), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Tag("No API key needed", Tone.Iris)
                    Tag("Encrypted", Tone.Mane)
                    Tag("Stop anytime", Tone.Neutral)
                }
                if (pendingLink) {
                    Spacer(Modifier.height(Space.lg))
                    Banner("A pairing link is waiting", Tone.Iris, body = "Finish this short setup and Nase will use it.", icon = NaseIcons.Link)
                }
                Spacer(Modifier.height(Space.xl))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.canvas)
                    .windowInsetsPadding(safeBottom())
                    .padding(horizontal = Space.gutter)
                    .padding(top = Space.sm, bottom = Space.md),
            ) {
                NaseButton("Get started", onStart, icon = NaseIcons.ArrowRight)
            }
        }
    }
}

private data class Promise(val icon: ImageVector, val title: String, val body: String, val tone: Tone)

@Composable
fun PrivacyScreen(
    checked: Boolean,
    pendingLink: Boolean,
    onChecked: (Boolean) -> Unit,
    onAgree: () -> Unit,
    onBack: () -> Unit,
    review: Boolean = false,
) {
    val promises = listOf(
        Promise(NaseIcons.Eye, "Only while you ask", "Nase sees the screen only during a session or a task you start.", Tone.Iris),
        Promise(NaseIcons.Lock, "Passwords stay hidden", "Password fields show as [password]. Nase won't read or type them.", Tone.Iris),
        Promise(NaseIcons.Shield, "It asks before anything risky", "Sending, paying, buying, deleting, calling, and security changes need your yes.", Tone.Mane),
        Promise(NaseIcons.Stop, "Stop means stop", "One tap ends the current task. Your connection stays, so the next ask just works.", Tone.Danger),
        Promise(NaseIcons.Key, "Your keys stay here", "API keys never leave this phone. Grok Bot doesn't need one.", Tone.Iris),
        Promise(NaseIcons.History, "History stays on the phone", "What you asked and each step are kept here. Clear them any time.", Tone.Neutral),
    )
    NasePage(
        title = "You stay in charge.",
        eyebrow = if (review) "Your privacy" else "Privacy",
        onBack = onBack,
        actions = { if (!review) PageDots(4, 1, Modifier.padding(end = Space.md)) },
        bottomBar = if (review) {
            null
        } else {
            {
                ConsentRow("I understand Nase can see the screen and act only while I allow it.", checked, onChecked)
                NaseButton("Agree and continue", onAgree, enabled = checked, icon = NaseIcons.ArrowRight)
            }
        },
    ) {
        if (pendingLink) Banner("A pairing link is waiting", Tone.Iris, body = "It will be used after setup.", icon = NaseIcons.Link)
        NaseCard(spacing = Space.lg) {
            promises.forEachIndexed { index, promise ->
                Row(Modifier.enter(index), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    IconTile(promise.icon, promise.tone)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(promise.title, style = Nase.type.bodyStrong, color = Nase.colors.ink)
                        Text(promise.body, style = Nase.type.bodySmall, color = Nase.colors.inkMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(
    items: List<SetupItem>,
    readiness: ReadinessState,
    restrictedHint: Boolean,
    samsung: Boolean,
    onboarding: Boolean,
    onAction: (String) -> Unit,
    onRestricted: () -> Unit,
    onSamsungBattery: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val done = items.count { it.done }
    NasePage(
        title = if (onboarding) "A few switches." else "Setup",
        eyebrow = if (onboarding) "Setup" else null,
        subtitle = "Nase control is the one Nase needs. The rest make it smoother.",
        onBack = onBack,
        actions = { if (onboarding) PageDots(4, 2, Modifier.padding(end = Space.md)) },
        bottomBar = if (onboarding) {
            {
                NaseButton(
                    if (readiness.accessibility) "Continue" else "Continue for now",
                    onContinue,
                    tone = if (readiness.accessibility) ButtonTone.Primary else ButtonTone.Secondary,
                    icon = NaseIcons.ArrowRight,
                )
            }
        } else {
            null
        },
    ) {
        NaseCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                ProgressRing(done / items.size.toFloat(), size = 52.dp, color = if (readiness.accessibility) Nase.colors.success else Nase.colors.iris)
                Column(Modifier.weight(1f)) {
                    Text("$done of ${items.size} done", style = Nase.type.titleSmall, color = Nase.colors.ink)
                    Text(
                        if (readiness.accessibility) "Nase is ready. The rest are nice to have." else "Turn on Nase control to let Nase act.",
                        style = Nase.type.bodySmall,
                        color = Nase.colors.inkMuted,
                    )
                }
            }
        }
        items.forEachIndexed { index, item ->
            SetupCard(item, index, onAction = { onAction(item.key) })
            if (item.key == "control" && restrictedHint) {
                Banner(
                    "Is the switch greyed out?",
                    Tone.Iris,
                    body = "On Android 13 and newer, open App info, tap the three-dot menu, then Allow restricted settings. Then come back and turn Nase control on.",
                    icon = NaseIcons.Info,
                    actions = listOf(BannerAction("Open App info", onRestricted)),
                )
            }
            if (item.key == "battery" && samsung) {
                TextAction("Samsung: add Nase to Never sleeping apps", onSamsungBattery, icon = NaseIcons.External)
            }
        }
    }
}

@Composable
private fun SetupCard(item: SetupItem, index: Int, onAction: () -> Unit) {
    val colors = Nase.colors
    val icon = when (item.key) {
        "control" -> NaseIcons.Accessibility
        "notifications" -> NaseIcons.Bell
        "battery" -> NaseIcons.Battery
        "microphone" -> NaseIcons.Mic
        else -> NaseIcons.Keyboard
    }
    NaseCard(Modifier.enter(index), border = if (!item.done && item.required) colors.iris.copy(alpha = 0.45f) else colors.hairline) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.Top) {
            IconTile(icon, if (item.done) Tone.Success else if (item.required) Tone.Iris else Tone.Neutral)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(item.title, style = Nase.type.titleSmall, color = colors.ink, modifier = Modifier.weight(1f, fill = false))
                    AnimatedContent(
                        targetState = item.done,
                        transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(120)) },
                        label = "setup-tag-${item.key}",
                    ) { finished ->
                        when {
                            finished -> Tag("Done", Tone.Success)
                            item.required -> Tag("Required", Tone.Iris)
                            else -> Box(Modifier)
                        }
                    }
                }
                Text(item.body, style = Nase.type.bodySmall, color = colors.inkMuted)
            }
        }
        if (!item.done) {
            NaseButton(
                item.action,
                onAction,
                tone = if (item.required) ButtonTone.Iris else ButtonTone.Secondary,
                height = 48.dp,
                haptic = Haptic.TAP,
            )
        }
    }
}

@Composable
fun TryItScreen(
    task: TaskRecord?,
    controlOn: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onTurnOn: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    shotFile: (String, String) -> java.io.File? = { _, _ -> null },
) {
    val colors = Nase.colors
    val haptics = rememberHaptics()
    var celebrated by remember { mutableStateOf(false) }
    LaunchedEffect(task?.state) {
        if (task?.state == TaskState.Done && !celebrated) {
            celebrated = true
            haptics.perform(Haptic.SUCCESS)
        }
    }
    val running = task?.running == true
    val done = task?.state == TaskState.Done
    NasePage(
        title = if (done) "It worked." else "Let's try it.",
        eyebrow = "First task",
        subtitle = if (done) "That was Nase, tapping Calculator for you. Next, connect Grok Bot for everything else." else "Watch Nase open Calculator and add 2\u00A0+\u00A02. It runs right on your phone, with no account or key.",
        onBack = onBack,
        actions = { PageDots(4, 3, Modifier.padding(end = Space.md)) },
        bottomBar = {
            when {
                running -> NaseButton("Stop", onStop, tone = ButtonTone.Danger, icon = NaseIcons.Stop, haptic = Haptic.STOP)
                done -> NaseButton("Go to Nase", onFinish, icon = NaseIcons.ArrowRight, haptic = Haptic.SUCCESS)
                !controlOn -> {
                    NaseButton("Turn on Nase control", onTurnOn, tone = ButtonTone.Iris, icon = NaseIcons.Accessibility)
                    NaseButton("Skip for now", onFinish, tone = ButtonTone.Quiet, haptic = Haptic.TAP)
                }
                else -> {
                    NaseButton(if (task == null) "Run it" else "Run it again", onRun, tone = ButtonTone.Iris, icon = NaseIcons.Play, haptic = Haptic.LISTEN)
                    NaseButton("Skip for now", onFinish, tone = ButtonTone.Quiet, haptic = Haptic.TAP)
                }
            }
        },
    ) {
        NaseCard(spacing = Space.lg) {
            Box(Modifier.fillMaxWidth().padding(top = Space.sm), contentAlignment = Alignment.Center) {
                PresenceOrb(task?.let { moodFor(it.state) } ?: OrbMood.Idle, size = 150.dp)
            }
            AnimatedContent(targetState = task?.state, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "try-state") { state ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (state) {
                        null -> {
                            Text("Try", style = Nase.type.overline, color = colors.iris)
                            Text("“Open Calculator and add 2\u00A0+\u00A02”", style = Nase.type.headline, color = colors.ink, textAlign = TextAlign.Center)
                        }
                        TaskState.Done -> {
                            Text("Calculator says", style = Nase.type.overline, color = colors.mane)
                            Text(task?.outcome ?: "2 + 2 = 4", style = Nase.type.display, color = colors.mane, textAlign = TextAlign.Center)
                        }
                        TaskState.Failed, TaskState.Stopped -> {
                            Text(if (state == TaskState.Stopped) "Stopped" else "That didn't work", style = Nase.type.titleSmall, color = colors.ink)
                            Text(task?.outcome.orEmpty(), style = Nase.type.bodySmall, color = colors.inkMuted, textAlign = TextAlign.Center)
                        }
                        else -> {
                            Text("Nase is working", style = Nase.type.overline, color = colors.iris)
                            Text(task?.headline ?: "Opening Calculator…", style = Nase.type.titleSmall, color = colors.ink, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (task != null && task.steps.isNotEmpty()) {
                StepTimeline(task.steps, task.createdAt, running, shotFile = { name -> shotFile(task.id, name) })
            }
        }
        if (!controlOn && task == null) {
            Banner(
                "Nase control is off",
                Tone.Warning,
                body = "Nase needs it to tap Calculator. Turn it on, come back, and run the try.",
                icon = NaseIcons.Accessibility,
            )
        }
    }
}
