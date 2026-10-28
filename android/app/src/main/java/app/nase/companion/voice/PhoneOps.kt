package app.nase.companion.voice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import app.nase.companion.a11y.NaseAccessibilityService
import app.nase.companion.a11y.TextEntry
import app.nase.companion.a11y.TypeMode
import app.nase.companion.brain.ScreenView
import app.nase.companion.brain.ToolCall
import app.nase.companion.display.ActionResult
import app.nase.companion.display.BackgroundHost
import app.nase.companion.display.DisplayPolicy
import app.nase.companion.display.ScreenRouter
import app.nase.companion.display.Watch
import app.nase.companion.memory.Memory
import app.nase.companion.memory.MemoryGuard
import app.nase.companion.overlay.AppVisibility
import app.nase.companion.recap.ActionExplanation
import app.nase.companion.recap.Bounds
import app.nase.companion.recap.MatchStrategy
import app.nase.companion.recap.OpMetrics
import app.nase.companion.recap.RecapShotPolicy
import app.nase.companion.recap.RecapShots
import app.nase.companion.recap.TapPoint
import app.nase.companion.recap.UndoLog
import app.nase.companion.recap.UndoPlanner
import app.nase.companion.recap.UndoableAction
import app.nase.companion.schedule.ScheduleAlarms
import app.nase.companion.schedule.ScheduleParser
import app.nase.companion.session.ActionExpiry
import app.nase.companion.session.AuditEntry
import app.nase.companion.session.AuditLog
import app.nase.companion.session.SessionRepository
import app.nase.companion.tasks.CarryBuffer
import app.nase.companion.tasks.StepKind
import app.nase.companion.tasks.StopState
import app.nase.companion.tasks.TaskRuntime
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Runs the same actions the relay uses, for a brain on this phone. Confirmation happens before this. */
object PhoneOps {
    /** Tools that don't touch the phone screen, so they run even when accessibility is off. */
    private val LOCAL_OPS = setOf("speak", "remember", "forget", "schedule_task", "copy_text", "recall_text")

    /**
     * Whether the run in progress succeeded. It starts true (local ops that return
     * normally succeed) and is cleared by [failed] or by [record] when an action
     * reported a failure. A ThreadLocal keeps two concurrent calls apart.
     */
    private val outcome = ThreadLocal<Boolean>()

    /** Extra attempts after the first for a screen failure that reported nothing was touched. */
    internal const val SCREEN_RETRIES = 2

    /** First retry delay; each further retry doubles it. */
    internal const val RETRY_BASE_MS = 200L

    /** No retry ever waits longer than this. */
    internal const val RETRY_MAX_MS = 1_000L

    /**
     * Screen errors that mean the action was refused before anything touched the
     * screen, so a retry cannot double-act: the guards ran out of time or the
     * accessibility service blinked. Gesture errors (tap_failed, gesture_failed,
     * swipe_failed, type_failed) are deliberately absent — a cancelled gesture may
     * already have partly acted.
     */
    private val TRANSIENT_SCREEN_ERRORS = setOf(
        "accessibility_off",
        ActionExpiry.SCREEN_CHANGED,
        DisplayPolicy.COVERED_BY_POPUP,
        DisplayPolicy.CALL_UI_FOREGROUND,
        DisplayPolicy.BACKGROUND_EMPTY,
    )

    fun screen(context: Context): ScreenView {
        val acted = ScreenRouter.tree(context, null)
        val tree = (acted.fields["tree"] as? String)?.take(8_000) ?: "(accessibility is off)"
        TaskRuntime.step(StepKind.Read, "Read the screen")
        return ScreenView(tree, screenshot(context))
    }

    fun execute(context: Context, call: ToolCall, watch: Watch = Watch.None): String {
        val started = System.nanoTime()
        outcome.set(true)
        try {
            return executeOp(context, call, watch)
        } catch (failure: Throwable) {
            outcome.set(false)
            throw failure
        } finally {
            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
            OpMetrics.record(call.name, outcome.get() ?: false, elapsedMs)
            outcome.remove()
        }
    }

    /** The real dispatch. [execute] wraps it so every branch is timed and counted exactly once. */
    private fun executeOp(context: Context, call: ToolCall, watch: Watch): String {
        if (StopState.gate.isStopped()) return failed("stopped")
        if (NaseAccessibilityService.instance == null && call.name !in LOCAL_OPS) return failed("accessibility_off")
        return when (call.name) {
            "tap" -> {
                val x = call.args["x"]?.toDoubleOrNull() ?: return failed("missing x")
                val y = call.args["y"]?.toDoubleOrNull() ?: return failed("missing y")
                val target = ScreenRouter.tapTarget(context, x, y, null)
                val label = target.label
                val acted = acting(watch) { ScreenRouter.tapReliably(context, x, y, label, null, watch) }
                val why = explain("tap", label, x, y, acted)
                val explained = withExplanation(acted, why)
                record(context, "tap", StepKind.Tap, if (label.isNotBlank()) "Tapped “${label.take(60)}”" else "Tapped the screen", explained, why)
                if (explained.ok) noteCommitIfSending(target)
                narrate(explained.ok, "tapped ${explained.fields["x"]},${explained.fields["y"]}", explained.error ?: "tap_failed", explained)
            }
            "swipe" -> {
                val x1 = call.args["x1"]?.toDoubleOrNull() ?: return failed("missing x1")
                val y1 = call.args["y1"]?.toDoubleOrNull() ?: return failed("missing y1")
                val x2 = call.args["x2"]?.toDoubleOrNull() ?: return failed("missing x2")
                val y2 = call.args["y2"]?.toDoubleOrNull() ?: return failed("missing y2")
                val dur = call.args["durationMs"]?.toLongOrNull() ?: 250L
                val acted = acting(watch) { ScreenRouter.swipe(context, x1, y1, x2, y2, dur, null, watch) }
                record(context, "swipe", StepKind.Swipe, swipeLabel(x1, y1, x2, y2), acted)
                narrate(acted.ok, "swiped", acted.error ?: "swipe_failed", acted)
            }
            "long_press" -> {
                val x = call.args["x"]?.toDoubleOrNull() ?: return failed("missing x")
                val y = call.args["y"]?.toDoubleOrNull() ?: return failed("missing y")
                val dur = call.args["durationMs"]?.toLongOrNull() ?: 600L
                val label = ScreenRouter.labelAt(context, x, y, null)
                val acted = acting(watch) { ScreenRouter.longPress(context, x, y, dur, null, watch) }
                val why = explain("long_press", label, x, y, acted)
                val explained = withExplanation(acted, why)
                record(context, "long_press", StepKind.Tap, if (label.isNotBlank()) "Held “${label.take(60)}”" else "Pressed and held", explained, why)
                narrate(explained.ok, "held ${explained.fields["x"]},${explained.fields["y"]}", explained.error ?: "gesture_failed", explained)
            }
            "drag" -> {
                val x1 = call.args["x1"]?.toDoubleOrNull() ?: return failed("missing x1")
                val y1 = call.args["y1"]?.toDoubleOrNull() ?: return failed("missing y1")
                val x2 = call.args["x2"]?.toDoubleOrNull() ?: return failed("missing x2")
                val y2 = call.args["y2"]?.toDoubleOrNull() ?: return failed("missing y2")
                val dur = call.args["durationMs"]?.toLongOrNull() ?: 600L
                val acted = acting(watch) { ScreenRouter.drag(context, x1, y1, x2, y2, dur, null, watch) }
                record(context, "drag", StepKind.Swipe, "Dragged to ${fmt(x2)},${fmt(y2)}", acted)
                narrate(acted.ok, "dragged", acted.error ?: "gesture_failed", acted)
            }
            "pinch" -> {
                val x = call.args["x"]?.toDoubleOrNull() ?: return failed("missing x")
                val y = call.args["y"]?.toDoubleOrNull() ?: return failed("missing y")
                val from = call.args["fromDistance"]?.toDoubleOrNull() ?: return failed("missing fromDistance")
                val to = call.args["toDistance"]?.toDoubleOrNull() ?: return failed("missing toDistance")
                val dur = call.args["durationMs"]?.toLongOrNull() ?: 300L
                val acted = acting(watch) { ScreenRouter.pinch(context, x, y, from, to, dur, null, watch) }
                record(context, "pinch", StepKind.Swipe, if (to >= from) "Pinched to zoom in" else "Pinched to zoom out", acted)
                narrate(acted.ok, if (to >= from) "zoomed in" else "zoomed out", acted.error ?: "gesture_failed", acted)
            }
            "type" -> {
                val text = call.args["text"] ?: return failed("missing text")
                val mode = TextEntry.parseMode(
                    call.args["mode"],
                    call.args["append"].equals("true", ignoreCase = true),
                )
                val acted = acting(watch) { ScreenRouter.type(context, text, mode, null, watch) }
                record(context, "type", StepKind.Type, "Typed ${text.length} characters", acted)
                if (acted.ok && mode != TypeMode.APPEND && text.isNotEmpty()) {
                    UndoLog.record(TaskRuntime.current()?.id, UndoableAction.ClearDraft(text.length, foregroundApp()))
                }
                narrate(
                    acted.ok,
                    "typed ${acted.fields["length"]} chars via ${acted.fields["method"]}",
                    acted.error ?: "type_failed",
                    acted,
                )
            }
            "key" -> {
                val key = call.args["key"] ?: return failed("missing key")
                val acted = acting(watch) { ScreenRouter.press(context, key, null, watch) }
                record(context, "key", StepKind.Key, "Pressed ${key.replaceFirstChar { it.uppercase() }}", acted)
                narrate(acted.ok, "pressed $key", acted.error ?: "unknown_key", acted)
            }
            "open_app" -> {
                val pkg = call.args["package"] ?: call.args["packageName"] ?: return failed("missing package")
                val acted = acting(watch) { ScreenRouter.open(context, pkg, null, consent = { prompt -> VoiceController.confirmBlocking(context, prompt) }, watch = watch) }
                record(context, "open_app", StepKind.Open, "Opened ${BackgroundHost.appLabel(context, pkg)}", acted)
                narrate(acted.ok, "opened $pkg", acted.error ?: "app_not_found", acted)
            }
            "open_settings" -> {
                val name = call.args["name"] ?: call.args["screen"] ?: return failed("missing name")
                val pkg = call.args["package"] ?: call.args["packageName"]
                val acted = acting(watch) { ScreenRouter.openSettings(context, name, pkg, null, consent = { prompt -> VoiceController.confirmBlocking(context, prompt) }, watch = watch) }
                record(context, "open_settings", StepKind.Open, "Opened ${settingsLabel(name)}", acted)
                narrate(acted.ok, "opened ${settingsLabel(name)}", acted.error ?: "unknown_settings", acted)
            }
            "wait_idle" -> {
                val timeout = call.args["timeoutMs"]?.toLongOrNull() ?: 4_000L
                val acted = acting(watch) { ScreenRouter.waitIdle(context, timeout, null) }
                val settled = acted.fields["settled"] == true
                val waited = acted.fields["waitedMs"]
                record(
                    context,
                    "wait_idle",
                    StepKind.Read,
                    if (settled) "Waited for the screen to settle" else "Screen still busy after ${waited}ms",
                    acted,
                )
                narrate(
                    acted.ok,
                    if (settled) "screen settled after ${waited}ms" else "still busy after ${waited}ms",
                    acted.error ?: "wait_failed",
                    acted,
                )
            }
            "speak" -> {
                val text = call.args["text"].orEmpty()
                VoiceController.speakNow(context, text)
                "spoke"
            }
            "remember" -> {
                val key = call.args["key"]?.trim().orEmpty()
                val value = call.args["value"]?.trim().orEmpty()
                if (key.isEmpty() || value.isEmpty()) return failed("missing key or value")
                val verdict = MemoryGuard.check(key, value)
                if (!verdict.allowed) {
                    TaskRuntime.step(StepKind.Status, "Won't remember “$key” — it looks private", false)
                    return failed("won't remember that — ${verdict.reason ?: "it looks private"}")
                }
                val entry = Memory.store(context).put(key, value)
                TaskRuntime.step(StepKind.Status, "Remembered “${entry.key.replace('_', ' ')}”")
                "remembered ${entry.key}"
            }
            "forget" -> {
                val key = call.args["key"]?.trim().orEmpty()
                if (key.isEmpty()) return failed("missing key")
                val removed = Memory.store(context).delete(key)
                TaskRuntime.step(StepKind.Status, if (removed) "Forgot “$key”" else "Nothing to forget for “$key”")
                if (removed) "forgot $key" else "nothing to forget for $key"
            }
            "schedule_task" -> {
                val taskText = call.args["task"]?.trim().orEmpty()
                val whenText = call.args["time"]?.trim().orEmpty()
                if (whenText.isEmpty()) return failed("missing time")
                val now = System.currentTimeMillis()
                val parsed = ScheduleParser.parse(whenText, now)
                    ?: ScheduleParser.parse("$whenText $taskText", now)
                if (parsed == null) {
                    TaskRuntime.step(StepKind.Status, "Couldn't tell when to run that", false)
                    return failed("couldn't tell when — say a time like 'every weekday at 8am' or 'tomorrow at 6pm'")
                }
                val text = taskText.ifBlank { parsed.task }
                if (text.isBlank()) return failed("what should I do at that time?")
                if (parsed.schedule.nextFireAt(now) == null) {
                    TaskRuntime.step(StepKind.Status, "That time's already past", false)
                    return failed("that time's already past — give me a later time")
                }
                val saved = ScheduleAlarms.add(context, text, parsed.schedule)
                val summary = saved.schedule.describe(now)
                TaskRuntime.step(StepKind.Status, "Scheduled “$text” — $summary")
                "scheduled $text — $summary"
            }
            "copy_text" -> {
                val label = call.args["label"]?.trim().orEmpty()
                val text = call.args["text"].orEmpty()
                if (label.isEmpty()) return failed("missing label")
                val result = CarryBuffer.shared.stash(label, text)
                if (!result.ok) {
                    TaskRuntime.step(StepKind.Status, "Didn't copy “$label” — ${result.reason}", false)
                    return failed("didn't copy — ${result.reason}")
                }
                val chars = result.note?.text?.length ?: 0
                TaskRuntime.step(StepKind.Status, "Copied “$label” to carry over")
                "copied $chars characters as $label"
            }
            "recall_text" -> {
                val label = call.args["label"]?.trim().orEmpty()
                if (label.isEmpty()) return failed("missing label")
                val text = CarryBuffer.shared.recall(label)
                if (text == null) {
                    TaskRuntime.step(StepKind.Status, "Nothing copied under “$label”", false)
                    return failed("nothing copied under $label")
                }
                TaskRuntime.step(StepKind.Status, "Recalled “$label”")
                text
            }
            else -> failed("unknown action ${call.name}")
        }
    }

    /**
     * Runs one screen action and retries only failures that reported nothing was
     * touched ([TRANSIENT_SCREEN_ERRORS]). A guard keeps its own waiting and the
     * display fallback is re-chosen on every attempt, so a retry never bypasses
     * them. Gesture failures are not retried: a cancelled gesture may have partly
     * landed, and repeating it could act twice.
     */
    private fun acting(watch: Watch, action: () -> ActionResult): ActionResult {
        var attempts = 0
        while (true) {
            val acted = action()
            if (acted.ok || !isTransientScreenFailure(acted.error)) return acted
            if (attempts >= SCREEN_RETRIES || StopState.gate.isStopped() || watch.cancelled()) return acted
            attempts++
            try {
                Thread.sleep(retryDelayMs(attempts))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return acted
            }
            // Stop may have been pressed during the backoff; do not act after it.
            if (StopState.gate.isStopped() || watch.cancelled()) return acted
        }
    }

    /** True only for errors whose action was refused before anything touched the screen. */
    internal fun isTransientScreenFailure(error: String?): Boolean = error != null && error in TRANSIENT_SCREEN_ERRORS

    /** 200 ms, 400 ms, 800 ms… capped at [RETRY_MAX_MS]. [attempt] is one-based. */
    internal fun retryDelayMs(attempt: Int): Long {
        val step = (attempt - 1).coerceIn(0, 16)
        return (RETRY_BASE_MS shl step).coerceAtMost(RETRY_MAX_MS)
    }

    /**
     * Why a tap or long-press touched what it did. Both deliver the coordinates
     * the brain supplied; [ScreenRouter] only looks up the label under that point,
     * so the pick is a [MatchStrategy.CoordinateFallback] unless the tap needed
     * the retry ladder's exact-label search to land.
     */
    internal fun explain(action: String, label: String, x: Double, y: Double, acted: ActionResult): ActionExplanation {
        val landedX = (acted.fields["x"] as? Number)?.toDouble() ?: x
        val landedY = (acted.fields["y"] as? Number)?.toDouble() ?: y
        val strategy = if (acted.fields["via"] == "search") MatchStrategy.ExactLabel else MatchStrategy.CoordinateFallback
        return ActionExplanation(
            action = action,
            label = label,
            strategy = strategy,
            bounds = windowBounds(acted),
            point = TapPoint(landedX, landedY),
        )
    }

    /** Carries the explanation on the action's wire fields without changing the display module. */
    internal fun withExplanation(acted: ActionResult, why: ActionExplanation): ActionResult =
        acted.copy(fields = acted.fields + ("explanation" to why.toJson()))

    /** The containing window's rectangle from the action result, or null when none was reported. */
    private fun windowBounds(acted: ActionResult): Bounds? {
        val raw = acted.fields["window"] as? String ?: return null
        val values = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
        if (values.size != 4) return null
        return Bounds(values[0], values[1], values[2], values[3])
    }

    private fun ActionExplanation.toJson(): JSONObject = JSONObject()
        .put("action", action)
        .put("label", label)
        .put("strategy", strategy.wire)
        .put("bounds", bounds?.let { JSONObject().put("left", it.left).put("top", it.top).put("right", it.right).put("bottom", it.bottom) } ?: JSONObject.NULL)
        .put("point", point?.let { JSONObject().put("x", it.x).put("y", it.y) } ?: JSONObject.NULL)
        .put("confidence", confidence ?: JSONObject.NULL)
        .put("candidates", candidates)
        .put("index", index ?: JSONObject.NULL)
        .put("ambiguous", ambiguous)

    /** Marks the current call failed and passes the message through to the caller. */
    private fun failed(message: String): String {
        outcome.set(false)
        return message
    }

    private fun record(context: Context, action: String, kind: StepKind, label: String, acted: ActionResult, why: ActionExplanation? = null) {
        outcome.set(acted.ok)
        val detail = acted.error ?: acted.target.name
        log(context, action, if (why == null) detail else detail + provenance(why), acted.ok)
        TaskRuntime.step(kind, if (acted.ok) label else "$label — ${acted.error}", acted.ok)
    }

    /** Compact provenance for the audit log: strategy, delivered point, containing window. */
    private fun provenance(why: ActionExplanation): String {
        val point = why.point?.let { "@${fmt(it.x)},${fmt(it.y)}" }.orEmpty()
        val box = why.bounds?.let { " [${it.left},${it.top},${it.right},${it.bottom}]" }.orEmpty()
        return " · ${why.strategy.wire}$point$box"
    }

    /**
     * Once Nase taps a control that sends, pays, posts, or deletes, the task is
     * committed and no undo is offered for it — the owner's rule is to never undo
     * anything already sent or paid. Matching uses the same tap target as the
     * confirm gate, so a bare icon on a money screen still commits.
     */
    private fun noteCommitIfSending(target: TapTarget) {
        val verdict = SafetyPolicy.forTap(target)
        if (verdict is Verdict.Confirm && UndoPlanner.committedBy(verdict.reason)) {
            UndoLog.commit(TaskRuntime.current()?.id)
        }
    }

    /** The app Nase is acting in right now, so an undo can reopen the right screen. */
    private fun foregroundApp(): String? =
        NaseAccessibilityService.instance?.foregroundPackage()?.takeIf { it.isNotBlank() }

    private fun settingsLabel(name: String): String = "${name.trim().replace('_', ' ')} settings"

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun swipeLabel(x1: Double, y1: Double, x2: Double, y2: Double): String {
        val dx = x2 - x1
        val dy = y2 - y1
        return when {
            kotlin.math.abs(dy) >= kotlin.math.abs(dx) && dy < 0 -> "Swiped up"
            kotlin.math.abs(dy) >= kotlin.math.abs(dx) -> "Swiped down"
            dx < 0 -> "Swiped left"
            else -> "Swiped right"
        }
    }

    private fun narrate(ok: Boolean, success: String, failure: String, acted: ActionResult): String {
        val where = " on ${acted.target.name}"
        val warn = acted.target.warnText?.let { " $it" }.orEmpty()
        return if (ok) success + where + warn else failure + where + warn
    }

    private fun screenshot(context: Context): String? {
        val acted = acting(Watch.None) { ScreenRouter.shot(context, null) }
        val jpeg = acted.fields["jpeg"] as? ByteArray ?: return null
        log(context, "screenshot", "${jpeg.size} bytes", true)
        TaskRuntime.step(StepKind.Look, "Looked at the screen", jpeg = jpeg, ofOtherApp = true)
        if (RecapShotPolicy.keep(AppVisibility.visible.value)) {
            RecapShots.capture(TaskRuntime.current()?.id, TaskRuntime.thumbnail(jpeg))
        }
        return shrink(jpeg)
    }

    private fun shrink(bytes: ByteArray): String? {
        val bitmap = if (bytes.size <= 120_000) {
            null
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        val encoded = if (bitmap == null) {
            if (bytes.size > 180_000) return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } else {
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * 0.45f).toInt().coerceAtLeast(1),
                (bitmap.height * 0.45f).toInt().coerceAtLeast(1),
                true,
            )
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 45, out)
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            val data = out.toByteArray()
            if (data.size > 180_000) null else Base64.encodeToString(data, Base64.NO_WRAP)
        }
        return encoded
    }

    private fun log(context: Context, action: String, detail: String, ok: Boolean) {
        val entries = AuditLog(context).append(AuditEntry(action = action, detail = detail, ok = ok))
        SessionRepository.update { it.copy(log = entries) }
    }
}
