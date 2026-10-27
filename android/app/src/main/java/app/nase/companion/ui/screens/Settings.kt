package app.nase.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nase.companion.memory.MemoryEntry
import app.nase.companion.routines.Routine
import app.nase.companion.schedule.ScheduledTask
import app.nase.companion.session.SessionLength
import app.nase.companion.ui.design.Banner
import app.nase.companion.ui.design.BannerAction
import app.nase.companion.ui.design.BrandMark
import app.nase.companion.ui.design.ButtonTone
import app.nase.companion.ui.design.EmptyState
import app.nase.companion.ui.design.Haptic
import app.nase.companion.ui.design.IconTile
import app.nase.companion.ui.design.ListGroup
import app.nase.companion.ui.design.ListRow
import app.nase.companion.ui.design.OrbMood
import app.nase.companion.ui.design.NaseButton
import app.nase.companion.ui.design.NaseCard
import app.nase.companion.ui.design.NaseIconButton
import app.nase.companion.ui.design.NaseIcons
import app.nase.companion.ui.design.NasePage
import app.nase.companion.ui.design.NaseSheet
import app.nase.companion.ui.design.NaseTextField
import app.nase.companion.ui.design.NaseToggle
import app.nase.companion.ui.design.PresenceOrb
import app.nase.companion.ui.design.ProgressRing
import app.nase.companion.ui.design.SectionHeader
import app.nase.companion.ui.design.Segmented
import app.nase.companion.ui.design.Skeleton
import app.nase.companion.ui.design.Tag
import app.nase.companion.ui.design.ToggleRow
import app.nase.companion.ui.design.Tone
import app.nase.companion.ui.design.Trailing
import app.nase.companion.ui.design.pressable
import app.nase.companion.ui.theme.Nase
import app.nase.companion.ui.theme.Shapes
import app.nase.companion.ui.theme.Space
import app.nase.companion.ui.theme.ThemeMode
import app.nase.companion.update.UpdateState
import app.nase.companion.voice.VoiceCatalog
import app.nase.companion.voice.VoicePrefs

data class SettingsState(
    val version: String,
    val versionCode: Int,
    val brainLabel: String,
    val connectionLabel: String,
    val sessionLength: SessionLength,
    val autoReconnect: Boolean,
    val connected: Boolean,
    val assistantName: String,
    val voiceEnabled: Boolean,
    val wakeWord: Boolean,
    val spokenReplies: Boolean,
    val background: Boolean,
    val mainScreen: String,
    val keepScreenshots: Boolean,
    val memoryCount: Int,
    val scheduleCount: Int,
    val routineCount: Int,
    val theme: ThemeMode,
    val update: UpdateState,
    val setupDone: Int,
    val setupTotal: Int,
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onBrains: () -> Unit,
    onConnection: () -> Unit,
    onSessionLength: (SessionLength) -> Unit,
    onAutoReconnect: (Boolean) -> Unit,
    onVoice: () -> Unit,
    onSpokenReplies: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onMainScreen: (String) -> Unit,
    onKeepScreenshots: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onUpdates: () -> Unit,
    onSetup: () -> Unit,
    onMemory: () -> Unit,
    onSchedules: () -> Unit,
    onRoutines: () -> Unit,
    onPrivacy: () -> Unit,
    onClearHistory: () -> Unit,
    onAdvanced: () -> Unit,
    onDisconnect: () -> Unit,
    onDisconnectEverything: () -> Unit,
) {
    var sheet by remember { mutableStateOf<String?>(null) }
    NasePage(
        title = "Settings",
        onBack = onBack,
        overlay = {
            NaseSheet(sheet == "length", { sheet = null }, "Stay connected for", subtitle = "After this, Nase disconnects and Grok Bot needs a new code. Time spent waiting for a call screen doesn't count.") {
                SessionLength.entries.forEach { length ->
                    ListRow(
                        length.label,
                        trailing = if (length == state.sessionLength) Trailing.Badge("Selected", Tone.Iris) else Trailing.None,
                        onClick = { onSessionLength(length); sheet = null },
                    )
                }
            }
            NaseSheet(sheet == "main", { sheet = null }, "Apps that can't work out of sight", subtitle = "Some apps, like banking apps, refuse Nase's hidden screen. Choose what Nase does then.") {
                listOf(
                    VoicePrefs.FALLBACK_ASK to ("Ask me each time" to "Nase asks before using your screen."),
                    VoicePrefs.FALLBACK_ALLOW to ("Use a pop-up window" to "The app opens in a small window on your screen."),
                    VoicePrefs.FALLBACK_NEVER to ("Never use my screen" to "Nase stops and explains instead."),
                ).forEach { (value, copy) ->
                    ListRow(
                        copy.first,
                        subtitle = copy.second,
                        trailing = if (value == state.mainScreen) Trailing.Badge("Selected", Tone.Iris) else Trailing.None,
                        onClick = { onMainScreen(value); sheet = null },
                    )
                }
            }
            NaseSheet(sheet == "clear", { sheet = null }, "Clear task history?", subtitle = "Deletes every task and screenshot kept on this phone.") {
                NaseButton("Clear history", { onClearHistory(); sheet = null }, tone = ButtonTone.Danger, icon = NaseIcons.Trash, haptic = Haptic.REJECT)
                NaseButton("Keep it", { sheet = null }, tone = ButtonTone.Secondary, haptic = Haptic.TAP)
            }
            NaseSheet(
                sheet == "disconnect",
                { sheet = null },
                "Disconnect everything?",
                subtitle = "Ends the session, removes every saved brain key from this phone, and stops Nase running in the background. You'll pair again and re-add any keys to use Nase.",
            ) {
                NaseButton("Disconnect everything", { onDisconnectEverything(); sheet = null }, tone = ButtonTone.Danger, icon = NaseIcons.Unplug, haptic = Haptic.REJECT)
                NaseButton("Keep things as they are", { sheet = null }, tone = ButtonTone.Secondary, haptic = Haptic.TAP)
            }
        },
    ) {
        NaseCard(onClick = onUpdates, onClickLabel = "Check for updates") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                BrandMark(size = 52.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Nase ${state.version}", style = Nase.type.title, color = Nase.colors.ink)
                    Text(updateLine(state.update), style = Nase.type.bodySmall, color = Nase.colors.inkMuted)
                }
                if (state.update is UpdateState.Available) Tag("Update", Tone.Mane)
            }
        }
        SectionHeader("Assistant")
        ListGroup(
            rows = buildList {
                if (state.connected) {
                    add {
                        ListRow(
                            "Disconnect ${state.assistantName}",
                            subtitle = "End the session now. Nase's own brain keeps answering.",
                            icon = NaseIcons.Unplug,
                            danger = true,
                            trailing = Trailing.None,
                            onClick = onDisconnect,
                        )
                    }
                }
                add { ListRow("Brain", icon = NaseIcons.Brain, trailing = Trailing.Value(state.brainLabel), onClick = onBrains) }
                add { ListRow("Connection", icon = NaseIcons.Cloud, trailing = Trailing.Value(state.connectionLabel), onClick = onConnection) }
                add { ListRow("Stay connected for", icon = NaseIcons.Clock, trailing = Trailing.Value(state.sessionLength.label), onClick = { sheet = "length" }) }
                add {
                    ToggleRow(
                        "Reconnect by itself",
                        state.autoReconnect,
                        onAutoReconnect,
                        subtitle = "After Wi-Fi drops, network switches, and restarts",
                        icon = NaseIcons.Refresh,
                    )
                }
            },
        )
        SectionHeader("Voice")
        ListGroup(
            rows = listOf(
                {
                    ListRow(
                        "Voice",
                        subtitle = if (state.wakeWord) "Assistant gesture and Hey Nase" else "Assistant gesture and the mic button",
                        icon = NaseIcons.Mic,
                        trailing = Trailing.Value(if (state.voiceEnabled) "On" else "Off"),
                        onClick = onVoice,
                    )
                },
                { ToggleRow("Read results aloud", state.spokenReplies, onSpokenReplies, subtitle = "For typed asks too. Spoken asks always answer out loud.", icon = NaseIcons.Speak) },
            ),
        )
        SectionHeader("While Nase works")
        ListGroup(
            rows = listOf(
                { ToggleRow("Work out of sight", state.background, onBackground, subtitle = "Nase keeps its actions off the screen you're using when it can", icon = NaseIcons.Layers) },
                {
                    ListRow(
                        "Apps that can't work out of sight",
                        icon = NaseIcons.Monitor,
                        trailing = Trailing.Value(
                            when (state.mainScreen) {
                                VoicePrefs.FALLBACK_ALLOW -> "Pop-up"
                                VoicePrefs.FALLBACK_NEVER -> "Never"
                                else -> "Ask"
                            },
                        ),
                        onClick = { sheet = "main" },
                    )
                },
                { ToggleRow("Save screenshots in history", state.keepScreenshots, onKeepScreenshots, subtitle = "Small copies, only on this phone", icon = NaseIcons.Image) },
            ),
        )
        SectionHeader("Appearance")
        Segmented(ThemeMode.entries.map { it to it.label }, state.theme, onTheme)
        SectionHeader("Privacy and setup")
        ListGroup(
            rows = listOf(
                { ListRow("Setup checklist", icon = NaseIcons.CheckCircle, trailing = Trailing.Value("${state.setupDone} of ${state.setupTotal}"), onClick = onSetup) },
                {
                    ListRow(
                        "What Nase remembers",
                        subtitle = "Names, preferences, and usual orders — kept on this phone",
                        icon = NaseIcons.Sparkles,
                        trailing = if (state.memoryCount > 0) Trailing.Value(if (state.memoryCount == 1) "1 fact" else "${state.memoryCount} facts") else Trailing.Chevron,
                        onClick = onMemory,
                    )
                },
                {
                    ListRow(
                        "Scheduled tasks",
                        subtitle = "Things Nase runs on its own — a morning rundown, a nightly reminder",
                        icon = NaseIcons.Timer,
                        trailing = if (state.scheduleCount > 0) Trailing.Value(if (state.scheduleCount == 1) "1 task" else "${state.scheduleCount} tasks") else Trailing.Chevron,
                        onClick = onSchedules,
                    )
                },
                {
                    ListRow(
                        "Saved routines",
                        subtitle = "Name a task you've finished, then run it again by saying its name",
                        icon = NaseIcons.Wand,
                        trailing = if (state.routineCount > 0) Trailing.Value(if (state.routineCount == 1) "1 routine" else "${state.routineCount} routines") else Trailing.Chevron,
                        onClick = onRoutines,
                    )
                },
                { ListRow("What Nase can and can't do", icon = NaseIcons.Shield, onClick = onPrivacy) },
                { ListRow("Clear task history", icon = NaseIcons.Trash, danger = true, trailing = Trailing.None, onClick = { sheet = "clear" }) },
            ),
        )
        SectionHeader("More")
        ListGroup(rows = listOf({ ListRow("Advanced", subtitle = "Keyboard, clipboard, and the optional shell display", icon = NaseIcons.Sliders, onClick = onAdvanced) }))
        SectionHeader("Start over")
        ListGroup(
            rows = listOf({
                ListRow(
                    "Disconnect everything",
                    subtitle = "Unpair, remove every saved brain key, and stop Nase in the background",
                    icon = NaseIcons.Unplug,
                    danger = true,
                    trailing = Trailing.None,
                    onClick = { sheet = "disconnect" },
                )
            }),
        )
        Text(
            "Nase Companion ${state.version} (${state.versionCode})",
            style = Nase.type.caption,
            color = Nase.colors.inkFaint,
            modifier = Modifier.fillMaxWidth().padding(top = Space.md),
            textAlign = TextAlign.Center,
        )
    }
}

private fun updateLine(state: UpdateState): String = when (state) {
    UpdateState.Idle -> "Tap to check for updates"
    UpdateState.Checking -> "Checking for updates…"
    is UpdateState.UpToDate -> "You're up to date"
    UpdateState.NoRelease -> "No newer release published yet"
    is UpdateState.Available -> "Nase ${state.manifest.versionName} is available"
    is UpdateState.Downloading -> "Downloading ${(state.progress * 100).toInt()}%"
    is UpdateState.Verifying -> "Checking the download…"
    is UpdateState.NeedsPermission -> "Allow Nase to install updates"
    is UpdateState.Installing -> "Installing…"
    is UpdateState.Failed -> state.message
}

data class VoiceState(
    val voiceEnabled: Boolean,
    val wakeWord: Boolean,
    val chargingOnly: Boolean,
    val micGranted: Boolean,
    val assistantHeld: Boolean,
    val speechRate: Float,
    val voices: List<VoiceCatalog.Option>,
    val voicesLoading: Boolean,
    val selectedVoice: String,
    val cloudVoice: Boolean,
    val wakeStatus: String,
    val fullScreenNeeded: Boolean,
)

@Composable
fun VoiceScreen(
    state: VoiceState,
    onVoiceEnabled: (Boolean) -> Unit,
    onWakeWord: (Boolean) -> Unit,
    onChargingOnly: (Boolean) -> Unit,
    onRequestMic: () -> Unit,
    onRate: (Float) -> Unit,
    onCloudVoice: (Boolean) -> Unit,
    onVoice: (String) -> Unit,
    onPreview: (String) -> Unit,
    onTest: () -> Unit,
    onAssistant: () -> Unit,
    onFullScreen: () -> Unit,
    onBack: () -> Unit,
) {
    NasePage(title = "Voice", subtitle = "Talk to Nase with the mic button, the assistant gesture, or Hey Nase.", onBack = onBack) {
        if (!state.micGranted) {
            Banner(
                "Nase can't hear you yet",
                Tone.Warning,
                body = "Allow the microphone so Nase can listen when you tap the mic. Speech is recognized on the phone.",
                icon = NaseIcons.MicOff,
                actions = listOf(BannerAction("Allow microphone", onRequestMic, primary = true, icon = NaseIcons.Mic)),
            )
        }
        ListGroup(
            rows = listOf(
                { ToggleRow("Voice", state.voiceEnabled, onVoiceEnabled, subtitle = "The mic button and the assistant gesture", icon = NaseIcons.Mic) },
                { ToggleRow("Hey Nase", state.wakeWord, onWakeWord, subtitle = "Off until you turn it on. Downloads a 40 MB on-device model the first time.", icon = NaseIcons.Waves, enabled = state.voiceEnabled) },
                { ToggleRow("Only while charging", state.chargingOnly, onChargingOnly, subtitle = "Hey Nase listens only when plugged in", icon = NaseIcons.Battery, enabled = state.wakeWord) },
            ),
        )
        if (state.wakeStatus.isNotBlank()) Banner(state.wakeStatus, Tone.Iris, icon = NaseIcons.Radio)
        SectionHeader("How Nase sounds")
        NaseCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speaking speed", style = Nase.type.bodyStrong, color = Nase.colors.ink, modifier = Modifier.weight(1f))
                Text("${"%.1f".format(state.speechRate)}×", style = Nase.type.mono, color = Nase.colors.iris)
            }
            Slider(
                value = state.speechRate,
                onValueChange = onRate,
                valueRange = 0.7f..1.4f,
                colors = SliderDefaults.colors(
                    thumbColor = Nase.colors.controlOn,
                    activeTrackColor = Nase.colors.controlOn,
                    inactiveTrackColor = Nase.colors.hairlineStrong,
                ),
                modifier = Modifier.semantics { contentDescription = "Speaking speed" },
            )
            NaseButton("Test voice", onTest, tone = ButtonTone.Secondary, icon = NaseIcons.Speak, height = 48.dp)
        }
        ToggleRow(
            "Cloud voices",
            state.cloudVoice,
            onCloudVoice,
            subtitle = if (state.cloudVoice) "Higher-quality voices that stream from the internet are in the list" else "Off. Nase only offers voices that run on your phone.",
            icon = NaseIcons.Cloud,
        )
        if (state.voicesLoading) {
            repeat(3) { Skeleton(Modifier.fillMaxWidth().height(56.dp), shape = Shapes.control) }
        } else {
            ListGroup(
                rows = buildList<@Composable () -> Unit> {
                    add {
                        VoiceRow(
                            place = "System default",
                            tier = "Your phone's pick",
                            cloud = false,
                            selected = state.selectedVoice.isEmpty(),
                            onSelect = { onVoice("") },
                            onPreview = { onPreview("") },
                        )
                    }
                    state.voices.take(8).forEach { voice ->
                        add {
                            VoiceRow(
                                place = VoiceCatalog.place(voice),
                                tier = VoiceCatalog.tier(voice.quality),
                                cloud = voice.networkRequired,
                                selected = voice.name == state.selectedVoice,
                                onSelect = { onVoice(voice.name) },
                                onPreview = { onPreview(voice.name) },
                            )
                        }
                    }
                },
            )
        }
        SectionHeader("Assistant gesture")
        ListGroup(
            rows = buildList<@Composable () -> Unit> {
                add {
                    ListRow(
                        "Digital assistant app",
                        subtitle = if (state.assistantHeld) "Nase is your assistant" else "Choose Nase Companion in the settings that open",
                        icon = NaseIcons.Hand,
                        trailing = if (state.assistantHeld) Trailing.Badge("On", Tone.Success) else Trailing.Chevron,
                        onClick = onAssistant,
                    )
                }
                if (state.fullScreenNeeded) {
                    add { ListRow("Wake the screen for Hey Nase", subtitle = "Allow full-screen alerts", icon = NaseIcons.Bell, onClick = onFullScreen) }
                }
            },
        )
        Banner(
            "On a Galaxy",
            Tone.Neutral,
            body = "The side button is often Bixby. If it only offers Bixby and Power off, use Settings → Apps → Choose default apps → Digital assistant app → Nase Companion, or Hey Nase.",
            icon = NaseIcons.Info,
        )
    }
}

data class ConnectionState(
    val cloud: Boolean,
    val privateRelay: String,
    val knownRelays: List<String>,
    val connected: Boolean,
    val statusLine: String,
    val relay: String?,
    val timeLeft: String?,
)

@Composable
fun ConnectionScreen(
    state: ConnectionState,
    error: String?,
    onCloud: () -> Unit,
    onSavePrivate: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTailscale: () -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(state.privateRelay) { mutableStateOf(state.privateRelay) }
    NasePage(title = "Connection", subtitle = "Every session is end-to-end encrypted. The relay only passes sealed messages along.", onBack = onBack) {
        if (state.connected) {
            NaseCard(border = Nase.colors.success.copy(alpha = 0.4f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    PresenceOrb(OrbMood.Idle, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(state.statusLine, style = Nase.type.bodyStrong, color = Nase.colors.ink)
                        Text(listOfNotNull(state.relay, state.timeLeft).joinToString(" · "), style = Nase.type.caption, color = Nase.colors.inkMuted)
                    }
                }
                NaseButton("Disconnect", onDisconnect, tone = ButtonTone.Secondary, icon = NaseIcons.Unplug, height = 48.dp)
            }
        }
        ChoiceCard(
            title = "Nase Cloud",
            body = "The easiest way. Grok Bot pairs here unless its link names another relay.",
            selected = state.cloud,
            tag = "Recommended",
            onClick = onCloud,
        )
        ChoiceCard(
            title = "Private network",
            body = "Tailscale or your own relay on your home network.",
            selected = !state.cloud,
            onClick = { if (state.privateRelay.isNotBlank()) onSavePrivate(state.privateRelay) },
        ) {
            NaseTextField(
                value = draft,
                onValueChange = { draft = it },
                label = "Relay address",
                placeholder = "http://100.x.y.z:8787",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                error = error,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                NaseButton("Save", { onSavePrivate(draft) }, enabled = draft.isNotBlank(), height = 48.dp, modifier = Modifier.weight(1f))
                NaseButton("About Tailscale", onTailscale, tone = ButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1f))
            }
        }
        if (state.knownRelays.isNotEmpty()) {
            SectionHeader("Relays you've used")
            ListGroup(rows = state.knownRelays.map { relay -> { ListRow(relay, icon = NaseIcons.Server, tone = Tone.Neutral, trailing = Trailing.None) } })
            Text("Pairing links for any of these are accepted, alongside Nase Cloud.", style = Nase.type.caption, color = Nase.colors.inkMuted)
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String? = null,
    content: @Composable () -> Unit = {},
) {
    val colors = Nase.colors
    NaseCard(onClick = onClick, onClickLabel = "Use $title", border = if (selected) colors.iris.copy(alpha = 0.6f) else colors.hairline) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            androidx.compose.material3.Icon(
                if (selected) NaseIcons.CheckCircle else NaseIcons.Circle,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) colors.iris else colors.control,
                modifier = Modifier.padding(2.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = Nase.type.titleSmall, color = colors.ink)
                    if (tag != null) Tag(tag, Tone.Iris)
                }
                Text(body, style = Nase.type.bodySmall, color = colors.inkMuted)
            }
        }
        content()
    }
}

data class AdvancedState(
    val clipboard: Boolean,
    val keyboardOn: Boolean,
    val shizuku: Boolean,
    val shizukuGranted: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val note: String?,
)

@Composable
fun AdvancedScreen(
    state: AdvancedState,
    onClipboard: (Boolean) -> Unit,
    onKeyboard: () -> Unit,
    onPicker: () -> Unit,
    onShizuku: (Boolean) -> Unit,
    onSetupShizuku: () -> Unit,
    onRestartShizuku: () -> Unit,
    onBack: () -> Unit,
) {
    NasePage(title = "Advanced", subtitle = "The technical bits. Most people never need these.", onBack = onBack) {
        SectionHeader("Typing")
        ListGroup(
            rows = buildList<@Composable () -> Unit> {
                add {
                    ToggleRow(
                        "Clipboard paste",
                        state.clipboard,
                        onClipboard,
                        subtitle = if (state.clipboard) "Used only when other ways to type fail, then your clipboard is restored" else "Off. Nase won't read or change your clipboard.",
                        icon = NaseIcons.Copy,
                    )
                }
                add {
                    ListRow(
                        "Nase keyboard",
                        subtitle = if (state.keyboardOn) "Enabled. It's never your default keyboard." else "Needed for Samsung Notes and a few other apps",
                        icon = NaseIcons.Keyboard,
                        trailing = if (state.keyboardOn) Trailing.Badge("On", Tone.Success) else Trailing.Chevron,
                        onClick = onKeyboard,
                    )
                }
                if (state.keyboardOn) add { ListRow("Switch keyboard now", icon = NaseIcons.Command, onClick = onPicker) }
            },
        )
        SectionHeader("Hidden screen")
        NaseCard {
            Text("Run apps out of sight (Shizuku)", style = Nase.type.titleSmall, color = Nase.colors.ink)
            Text(
                "Shizuku lets Nase run apps on a hidden screen. Nase walks you through turning it on, one step at a time.",
                style = Nase.type.bodySmall,
                color = Nase.colors.inkMuted,
            )
            val status = when {
                state.shizukuGranted -> "Granted"
                !state.shizukuInstalled -> "Not installed yet"
                state.shizukuRunning -> "Running — not granted yet"
                else -> "Installed — not running"
            }
            ToggleRow("Use Shizuku", state.shizuku, onShizuku, subtitle = status, icon = NaseIcons.Terminal)
            when {
                state.shizukuGranted ->
                    Text("Apps can now run on Nase's hidden screen.", style = Nase.type.bodySmall, color = Nase.colors.success)
                state.shizukuInstalled && !state.shizukuRunning -> {
                    NaseButton("Restart Shizuku", onRestartShizuku, tone = ButtonTone.Primary, height = 48.dp, icon = NaseIcons.Refresh)
                    Text(
                        "Shizuku stops after each reboot. Open it and tap Start to use the hidden screen again.",
                        style = Nase.type.bodySmall,
                        color = Nase.colors.inkMuted,
                    )
                }
                else -> NaseButton("Set up Shizuku", onSetupShizuku, tone = ButtonTone.Primary, height = 48.dp, icon = NaseIcons.Shield)
            }
            state.note?.let { Text(it, style = Nase.type.bodySmall, color = Nase.colors.iris) }
        }
        Banner(
            "Wireless debugging over Tailscale",
            Tone.Neutral,
            body = "Power users can run adb connect 100.x.y.z:5555. Nase doesn't control the phone that way; pairing still uses the relay.",
            icon = NaseIcons.Info,
        )
    }
}

@Composable
fun UpdatesScreen(
    state: UpdateState,
    version: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onAllowInstalls: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = Nase.colors
    NasePage(
        title = "Updates",
        subtitle = "Updates come from download.nase.karlmagendavid.com and install only if they're signed like your Nase.",
        onBack = onBack,
        bottomBar = {
            when (state) {
                is UpdateState.Available -> NaseButton("Download and install", onDownload, icon = NaseIcons.Download)
                is UpdateState.NeedsPermission -> NaseButton("Allow installing updates", onAllowInstalls, tone = ButtonTone.Iris, icon = NaseIcons.Shield)
                UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Verifying, is UpdateState.Installing ->
                    NaseButton("Working…", {}, enabled = false, loading = true, tone = ButtonTone.Secondary)
                else -> NaseButton("Check for updates", onCheck, tone = ButtonTone.Secondary, icon = NaseIcons.Refresh)
            }
        },
    ) {
        NaseCard(spacing = Space.lg) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (state) {
                    is UpdateState.Downloading -> Box(contentAlignment = Alignment.Center) {
                        ProgressRing(state.progress, size = 120.dp)
                        Text("${(state.progress * 100).toInt()}%", style = Nase.type.title, color = colors.ink)
                    }
                    else -> PresenceOrb(
                        when (state) {
                            UpdateState.Checking, is UpdateState.Verifying, is UpdateState.Installing -> OrbMood.Thinking
                            is UpdateState.Available -> OrbMood.Success
                            is UpdateState.Failed -> OrbMood.Error
                            else -> OrbMood.Idle
                        },
                        size = 120.dp,
                    )
                }
            }
            Text(
                when (state) {
                    UpdateState.Idle -> "Nase $version"
                    UpdateState.Checking -> "Checking…"
                    is UpdateState.UpToDate -> "You're up to date"
                    UpdateState.NoRelease -> "No newer release yet"
                    is UpdateState.Available -> "Nase ${state.manifest.versionName}"
                    is UpdateState.Downloading -> "Downloading Nase ${state.manifest.versionName}"
                    is UpdateState.Verifying -> "Checking the signature…"
                    is UpdateState.NeedsPermission -> "One permission first"
                    is UpdateState.Installing -> "Installing…"
                    is UpdateState.Failed -> "Couldn't update"
                },
                style = Nase.type.headline,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                when (state) {
                    UpdateState.Idle -> "You have version $version."
                    UpdateState.Checking -> "Asking the Nase download server."
                    is UpdateState.UpToDate -> "Version $version is the newest."
                    UpdateState.NoRelease -> "The download server hasn't published anything newer than $version."
                    is UpdateState.Available -> listOfNotNull("You have $version", state.manifest.publishedAt?.let { "released $it" }).joinToString(" · ")
                    is UpdateState.Downloading -> "Keep Nase open until it finishes."
                    is UpdateState.Verifying -> "Nase checks the checksum and that it's signed with your Nase's key."
                    is UpdateState.NeedsPermission -> "Android asks you once to let Nase install its own updates."
                    is UpdateState.Installing -> "Android will ask you to confirm."
                    is UpdateState.Failed -> state.message
                },
                style = Nase.type.body,
                color = colors.inkMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        val manifest = when (state) {
            is UpdateState.Available -> state.manifest
            is UpdateState.Downloading -> state.manifest
            is UpdateState.NeedsPermission -> state.manifest
            is UpdateState.Failed -> state.manifest
            else -> null
        }
        if (manifest != null && manifest.changelog.isNotEmpty()) {
            SectionHeader("What's new")
            NaseCard {
                manifest.changelog.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        androidx.compose.material3.Icon(NaseIcons.Sparkles, contentDescription = null, tint = colors.mane, modifier = Modifier.padding(top = 2.dp))
                        Text(line, style = Nase.type.bodySmall, color = colors.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (state == UpdateState.Checking) {
            repeat(3) { Skeleton(Modifier.fillMaxWidth().height(20.dp)) }
        }
    }
}

data class MemoryState(
    val entries: List<MemoryEntry>,
    val loading: Boolean,
)

@Composable
fun MemoryScreen(
    state: MemoryState,
    onForget: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    NasePage(
        title = "Memory",
        subtitle = "Facts you've asked Nase to keep — your name, where you live, a usual order. Kept encrypted on this phone, and never passwords or codes.",
        onBack = onBack,
        actions = {
            if (state.entries.isNotEmpty()) NaseIconButton(NaseIcons.Trash, "Forget everything", { confirmClear = true })
        },
        overlay = {
            NaseSheet(
                visible = confirmClear,
                onDismiss = { confirmClear = false },
                title = "Forget everything?",
                subtitle = "Nase will lose every fact below. It can't be undone.",
            ) {
                NaseButton("Forget all", { confirmClear = false; onClearAll() }, tone = ButtonTone.Danger, icon = NaseIcons.Trash, haptic = Haptic.REJECT)
                NaseButton("Keep them", { confirmClear = false }, tone = ButtonTone.Secondary, haptic = Haptic.TAP)
            }
        },
    ) {
        when {
            state.loading -> repeat(3) { Skeleton(Modifier.fillMaxWidth().height(60.dp), shape = Shapes.control) }
            state.entries.isEmpty() -> EmptyState(
                title = "Nothing remembered yet",
                body = "Tell Nase something worth keeping — “remember my coffee is an oat flat white” — and it shows up here, so Nase won't ask twice.",
                icon = NaseIcons.Brain,
            )
            else -> {
                SectionHeader(if (state.entries.size == 1) "1 fact" else "${state.entries.size} facts")
                ListGroup(
                    rows = state.entries.map { entry ->
                        { MemoryRow(memoryLabel(entry.key), entry.value) { onForget(entry.key) } }
                    },
                )
                Text(
                    "Tap the bin to forget one. Nase can also forget when you ask it to.",
                    style = Nase.type.caption,
                    color = Nase.colors.inkFaint,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm, start = Space.xs),
                )
            }
        }
    }
}

@Composable
private fun MemoryRow(label: String, value: String, onForget: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        IconTile(NaseIcons.Sparkles, Tone.Iris)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = Nase.type.bodyStrong, color = Nase.colors.ink)
            Text(value, style = Nase.type.body, color = Nase.colors.inkMuted)
        }
        NaseIconButton(NaseIcons.Trash, "Forget $label", onForget)
    }
}

private fun memoryLabel(key: String): String =
    key.replace('_', ' ').replaceFirstChar { it.uppercase() }

data class SchedulesState(
    val tasks: List<ScheduledTask>,
    val loading: Boolean,
    val now: Long,
)

@Composable
fun SchedulesScreen(
    state: SchedulesState,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    NasePage(
        title = "Scheduled tasks",
        subtitle = "Things you've asked Nase to do on its own. Nase runs each one at its time and tells you how it went.",
        onBack = onBack,
        actions = {
            if (state.tasks.isNotEmpty()) NaseIconButton(NaseIcons.Trash, "Delete all", { confirmClear = true })
        },
        overlay = {
            NaseSheet(
                visible = confirmClear,
                onDismiss = { confirmClear = false },
                title = "Delete every task?",
                subtitle = "Nase will stop running all of them. It can't be undone.",
            ) {
                NaseButton("Delete all", { confirmClear = false; onClearAll() }, tone = ButtonTone.Danger, icon = NaseIcons.Trash, haptic = Haptic.REJECT)
                NaseButton("Keep them", { confirmClear = false }, tone = ButtonTone.Secondary, haptic = Haptic.TAP)
            }
        },
    ) {
        when {
            state.loading -> repeat(3) { Skeleton(Modifier.fillMaxWidth().height(68.dp), shape = Shapes.control) }
            state.tasks.isEmpty() -> EmptyState(
                title = "No scheduled tasks",
                body = "Ask Nase to do something on a repeat — “every morning at 7, read me my calendar” — and it shows up here, ready to run on its own.",
                icon = NaseIcons.Timer,
            )
            else -> {
                SectionHeader(if (state.tasks.size == 1) "1 task" else "${state.tasks.size} tasks")
                ListGroup(
                    rows = state.tasks.map { task ->
                        { ScheduleRow(task, state.now, { on -> onToggle(task.id, on) }, { onDelete(task.id) }) }
                    },
                )
                Text(
                    "Turn one off to pause it, or tap the bin to remove it. You can also just tell Nase to stop one.",
                    style = Nase.type.caption,
                    color = Nase.colors.inkFaint,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm, start = Space.xs),
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(task: ScheduledTask, now: Long, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        IconTile(if (task.schedule.repeats) NaseIcons.Timer else NaseIcons.AlarmClock, if (task.enabled) Tone.Iris else Tone.Neutral)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                task.text.replaceFirstChar { it.uppercase() },
                style = Nase.type.bodyStrong,
                color = if (task.enabled) Nase.colors.ink else Nase.colors.inkMuted,
            )
            Text(task.schedule.describe(now), style = Nase.type.bodySmall, color = Nase.colors.inkMuted)
        }
        NaseToggle(task.enabled, onToggle)
        NaseIconButton(NaseIcons.Trash, "Delete ${task.text}", onDelete)
    }
}

data class RoutinesState(
    val routines: List<Routine>,
    val loading: Boolean,
)

@Composable
fun RoutinesScreen(
    state: RoutinesState,
    onRun: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Routine?>(null) }
    var draft by remember { mutableStateOf("") }
    NasePage(
        title = "Saved routines",
        subtitle = "Tasks you've named so you can run them again just by saying the name. Every Send this? and Pay? still asks first.",
        onBack = onBack,
        actions = {
            if (state.routines.isNotEmpty()) NaseIconButton(NaseIcons.Trash, "Delete all", { confirmClear = true })
        },
        overlay = {
            NaseSheet(
                visible = confirmClear,
                onDismiss = { confirmClear = false },
                title = "Delete every routine?",
                subtitle = "Nase will forget all of them. It can't be undone.",
            ) {
                NaseButton("Delete all", { confirmClear = false; onClearAll() }, tone = ButtonTone.Danger, icon = NaseIcons.Trash, haptic = Haptic.REJECT)
                NaseButton("Keep them", { confirmClear = false }, tone = ButtonTone.Secondary, haptic = Haptic.TAP)
            }
            val target = renaming
            NaseSheet(
                visible = target != null,
                onDismiss = { renaming = null },
                title = "Rename routine",
                subtitle = target?.let { "Say this name to run “${it.text.take(60)}”." },
            ) {
                NaseTextField(
                    draft,
                    { draft = it.take(60) },
                    label = "Name",
                    placeholder = "my usual lunch",
                    singleLine = true,
                )
                NaseButton(
                    "Save name",
                    {
                        val id = target?.id
                        val name = draft.trim()
                        renaming = null
                        if (id != null && name.isNotEmpty()) onRename(id, name)
                    },
                    icon = NaseIcons.Check,
                    haptic = Haptic.CONFIRM,
                )
                NaseButton("Cancel", { renaming = null }, tone = ButtonTone.Secondary, haptic = Haptic.TAP)
            }
        },
    ) {
        when {
            state.loading -> repeat(3) { Skeleton(Modifier.fillMaxWidth().height(68.dp), shape = Shapes.control) }
            state.routines.isEmpty() -> EmptyState(
                title = "No saved routines",
                body = "Finish a task, then say “save that as my usual lunch.” It shows up here, ready to run again whenever you say its name.",
                icon = NaseIcons.Wand,
            )
            else -> {
                SectionHeader(if (state.routines.size == 1) "1 routine" else "${state.routines.size} routines")
                ListGroup(
                    rows = state.routines.map { routine ->
                        {
                            RoutineRow(
                                routine,
                                onRun = { onRun(routine.id) },
                                onRename = { renaming = routine; draft = routine.name },
                                onDelete = { onDelete(routine.id) },
                            )
                        }
                    },
                )
                Text(
                    "Tap a routine to run it now, the pencil to rename it, or the bin to remove it.",
                    style = Nase.type.caption,
                    color = Nase.colors.inkFaint,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.sm, start = Space.xs),
                )
            }
        }
    }
}

@Composable
private fun RoutineRow(routine: Routine, onRun: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .pressable(onClick = onRun, label = "Run ${routine.name}", haptic = Haptic.TAP, pressedScale = 0.985f)
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        IconTile(NaseIcons.Wand, Tone.Iris)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(routine.name.replaceFirstChar { it.uppercase() }, style = Nase.type.bodyStrong, color = Nase.colors.ink)
            Text(
                routine.text,
                style = Nase.type.bodySmall,
                color = Nase.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NaseIconButton(NaseIcons.Pencil, "Rename ${routine.name}", onRename)
        NaseIconButton(NaseIcons.Trash, "Delete ${routine.name}", onDelete)
    }
}

@Composable
private fun VoiceRow(
    place: String,
    tier: String,
    cloud: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    val colors = Nase.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .pressable(onClick = onSelect, label = place, haptic = Haptic.SELECT, pressedScale = 0.985f)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Icon(
            if (selected) NaseIcons.CheckCircle else NaseIcons.Circle,
            contentDescription = if (selected) "Selected" else null,
            tint = if (selected) colors.iris else colors.control,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(place, style = Nase.type.bodyStrong, color = colors.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(tier, style = Nase.type.bodySmall, color = colors.inkMuted)
                if (cloud) Tag("Cloud", Tone.Neutral)
            }
        }
        NaseIconButton(NaseIcons.Speak, "Preview $place", onPreview)
    }
}
