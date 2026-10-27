package app.nase.companion

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import app.nase.companion.a11y.NaseAccessibilityService
import app.nase.companion.brain.GrokArrival
import app.nase.companion.brain.PONY_GROK_BOT_TEMPLATE_URL
import app.nase.companion.display.ShizukuBridge
import app.nase.companion.net.CleartextPolicy
import app.nase.companion.overlay.AppVisibility
import app.nase.companion.proto.PairingLinks
import app.nase.companion.proto.PairingPayload
import app.nase.companion.session.Connection
import app.nase.companion.session.NaseSessionService
import app.nase.companion.session.Readiness
import app.nase.companion.session.SessionRepository
import app.nase.companion.ui.NaseActions
import app.nase.companion.ui.NaseRoot
import app.nase.companion.ui.NaseViewModel
import app.nase.companion.ui.QrScanner
import app.nase.companion.ui.Route
import app.nase.companion.ui.theme.NaseTheme
import app.nase.companion.ui.theme.resolveDark
import app.nase.companion.voice.SpeechOutput
import app.nase.companion.voice.VoiceController
import app.nase.companion.voice.VoicePrefs
import app.nase.companion.voice.WakeWordService
import org.json.JSONObject
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val vm: NaseViewModel by viewModels()
    private var listenAfterMic = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val qr = vm.pendingPayload
        vm.finishCapture()
        if (result.resultCode == RESULT_OK && result.data != null && qr != null) {
            SessionRepository.update {
                it.copy(connection = Connection.Pairing, status = "Starting the session…", lastError = null)
            }
            NaseSessionService.start(this, qr, result.resultCode, result.data)
        } else {
            if (qr != null) vm.holdLink(qr)
            vm.scanError = "Screen sharing was declined. Nase asks once per session and won't connect without it."
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Readiness.refresh(this)
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Readiness.refresh(this)
    }

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Readiness.refresh(this)
        syncWake()
        if (granted && listenAfterMic) {
            listenAfterMic = false
            startListening()
        } else if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            vm.scanError = null
            openAppDetails()
        }
    }

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        vm.shizukuGranted = result == PackageManager.PERMISSION_GRANTED
        if (vm.shizukuGranted) vm.updateShizuku(true)
        vm.note = if (vm.shizukuGranted) "Shizuku granted. Apps can run on the hidden shell display." else "Shizuku did not grant access."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            NaseTheme(vm.themeMode) {
                SystemBars(resolveDark(vm.themeMode))
                NaseRoot(vm = vm, actions = actions(), scanner = { onQr -> QrScanner(onQr = onQr) })
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.set(true)
        NaseAccessibilityService.instance?.releaseKeyboard()
    }

    override fun onStop() {
        AppVisibility.set(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        showSystemBars()
        Readiness.refresh(this)
        if (vm.captureStale()) vm.finishCapture()
        vm.reload()
        vm.refreshShizuku()
        syncWake()
    }

    /** The clock, signal, and battery stay visible whenever Nase is open. */
    private fun showSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun actions() = NaseActions(
        openAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        openAppDetails = ::openAppDetails,
        requestNotifications = {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings()
            }
        },
        requestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
        requestMic = { listenAfter ->
            listenAfterMic = listenAfter
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        requestBattery = ::requestBattery,
        openSamsungBattery = ::openSamsungBattery,
        openKeyboardSettings = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
        showKeyboardPicker = { getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() },
        openAssistantSettings = ::openAssistantSettings,
        openFullScreenSettings = ::openFullScreenSettings,
        openInstallPermission = {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        },
        pair = ::beginPairing,
        usePending = { vm.pendingLink?.let(::beginPairing) },
        disconnect = { NaseSessionService.stop(this) },
        reconnect = {
            if (!NaseSessionService.resume(this)) {
                vm.refreshResumable()
                vm.nav.push(Route.Pair)
            }
        },
        retry = { NaseSessionService.retry(this) },
        grantShizuku = ::grantShizuku,
        getShizuku = ::getShizuku,
        openShizuku = ::openShizuku,
        openBuildNumber = { openDeepLink(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), Intent(Settings.ACTION_SETTINGS)) },
        openWirelessDebugging = ::openWirelessDebugging,
        openTemplate = { openUrl(PONY_GROK_BOT_TEMPLATE_URL) },
        openTailscale = { openUrl(CleartextPolicy.TAILSCALE_URL) },
        openUrl = ::openUrl,
        testVoice = ::testVoice,
        previewVoice = ::previewVoice,
        setWakeWord = { enabled ->
            vm.updateWakeWord(enabled)
            if (enabled && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                syncWake()
            }
        },
        setVoiceEnabled = { enabled ->
            vm.updateVoiceEnabled(enabled)
            syncWake()
        },
        copy = { text ->
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Nase", text))
        },
        listen = ::startListening,
        micGranted = { ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
    )

    private fun startListening() {
        if (vm.nav.current !is Route.Ask) vm.nav.push(Route.Ask(listen = false))
        VoiceController.listen(this, "button")
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_REQUEST_MIC, false)) {
            intent.removeExtra(EXTRA_REQUEST_MIC)
            listenAfterMic = false
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val raw = intent.dataString
        val client = intent.getStringExtra("client") ?: PairingLinks.clientParam(raw)
        val relay = raw?.let { link ->
            if (!PairingLinks.looksLikePairing(link)) null else runCatching { PairingLinks.fields(link).relay }.getOrNull()
        }
        if (GrokArrival.matches(client)) vm.applyGrokTemplate(relay)
        if (intent.action != Intent.ACTION_VIEW || raw == null || !PairingLinks.looksLikePairing(raw)) return
        if (runCatching { PairingLinks.fields(raw) }.isFailure) return
        intent.data = null
        beginPairing(raw)
    }

    /**
     * Pairs from a scanned code or a link. The only guards are live state: a
     * capture prompt already on screen, or a session already running. Nothing
     * latches, so a link after a remote disconnect works the first time.
     */
    private fun beginPairing(raw: String) {
        if (vm.captureInFlight) {
            // A screen-capture prompt is already up. If it's gone stale (the owner
            // tapped away and it never came back), clear it and pair now; otherwise
            // say so instead of swallowing the scan.
            if (vm.captureStale()) {
                vm.finishCapture()
            } else {
                vm.scanError = "Finishing the last pairing attempt — try again in a moment."
                if (vm.nav.current != Route.Pair && !vm.nav.current.onboarding) vm.nav.push(Route.Pair)
                return
            }
        }
        val connection = SessionRepository.snapshot().connection
        if (connection == Connection.Connected || connection == Connection.Pairing || connection == Connection.Ending) {
            vm.scanError = "A session is already running. Disconnect before pairing again."
            if (vm.nav.current != Route.Pair) vm.nav.push(Route.Pair)
            return
        }
        val json = try {
            normalizePairing(raw)
        } catch (expired: PairingLinks.PairingLinkExpired) {
            vm.scanError = expired.message
            if (vm.nav.current != Route.Pair && !vm.nav.current.onboarding) vm.nav.push(Route.Pair)
            return
        } catch (_: Exception) {
            vm.scanError = "That isn't a Nase pairing code."
            return
        }
        val relay = try {
            PairingPayload.parse(json).relay
        } catch (_: Exception) {
            vm.scanError = "That isn't a Nase pairing code."
            return
        }
        if (!CleartextPolicy.allows(relay)) {
            vm.scanError = "That relay address isn't allowed. Use Nase Cloud, Tailscale, or your home network."
            return
        }
        if (!vm.hasConsented() || !Readiness.state.value.accessibility) {
            vm.holdLink(json)
            vm.scanError = if (!vm.hasConsented()) {
                "A pairing link is waiting. Finish setup and turn on Nase control, then use it."
            } else {
                "Turn on Nase control, then use the waiting pairing link."
            }
            return
        }
        if (connection == Connection.Reconnecting || connection == Connection.Error) NaseSessionService.stop(this)
        CleartextPolicy.canonical(relay)?.let { VoicePrefs.rememberRelay(this, it) }
        vm.scanError = null
        vm.beginCapture(json)
        if (vm.nav.current != Route.Pair && !vm.nav.current.onboarding) vm.nav.push(Route.Pair)
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun normalizePairing(raw: String): String {
        val trimmed = raw.trim()
        if (PairingLinks.looksLikePairing(trimmed)) {
            val fields = PairingLinks.fields(trimmed)
            if (PairingLinks.isExpired(fields)) throw PairingLinks.PairingLinkExpired()
            val json = JSONObject()
                .put("v", fields.v.toIntOrNull() ?: -1)
                .put("relay", fields.relay)
                .put("token", fields.token)
                .put("pk", fields.pk)
                .toString()
            PairingPayload.parse(json)
            return json
        }
        PairingPayload.parse(trimmed)
        return trimmed
    }

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }.onFailure { openAppDetails() }
    }

    private fun openAssistantSettings() {
        val voice = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        val defaults = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        val target = if (voice.resolveActivity(packageManager) != null) voice else defaults
        if (target.resolveActivity(packageManager) != null) startActivity(target)
    }

    private fun requestBattery() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val target = if (direct.resolveActivity(packageManager) != null) direct else fallback
        runCatching { startActivity(target) }
    }

    /** Samsung's "Never sleeping apps" lives in Device care. Its screen names vary by One UI version. */
    private fun openSamsungBattery() {
        val candidates = listOf(
            Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            Intent().setClassName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        val target = candidates.firstOrNull { it.resolveActivity(packageManager) != null }
        if (target != null) runCatching { startActivity(target) } else openAppDetails()
    }

    private fun grantShizuku() {
        try {
            if (!ShizukuBridge.installed(this)) {
                vm.note = "Install Shizuku, start it once with wireless debugging, then tap Grant."
                return
            }
            if (!ShizukuBridge.running()) {
                vm.note = "Open Shizuku and start it, then come back and tap Grant."
                return
            }
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
            Shizuku.addRequestPermissionResultListener(shizukuListener)
            ShizukuBridge.requestPermission(41)
        } catch (_: Throwable) {
            vm.note = "Shizuku isn't running. Start it with wireless debugging, then tap Grant."
        }
    }

    private val shizukuPackage = "moe.shizuku.privileged.api"

    /** Opens the official Play Store listing, then the web listing, then GitHub releases. */
    private fun getShizuku() {
        val play = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$shizukuPackage")).setPackage("com.android.vending")
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$shizukuPackage"))
        val github = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
        if (!openFirst(play, web, github)) vm.note = "Couldn't open a store. Visit github.com/RikkaApps/Shizuku/releases to get Shizuku."
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage(shizukuPackage)
        if (launch != null) {
            if (!openFirst(launch)) vm.note = "Couldn't open Shizuku."
        } else {
            vm.note = "Shizuku isn't installed yet. Tap Get Shizuku first."
            getShizuku()
        }
    }

    /**
     * Jumps straight to the Wireless debugging screen. The quick-settings-tile
     * route works on Android 12+; older builds fall back to Developer options.
     */
    private fun openWirelessDebugging() {
        val tile = Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES").putExtra(
            Intent.EXTRA_COMPONENT_NAME,
            android.content.ComponentName("com.android.settings", "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging"),
        )
        val subSettings = Intent().setClassName("com.android.settings", "com.android.settings.SubSettings")
            .putExtra(":settings:show_fragment", "com.android.settings.development.WirelessDebuggingFragment")
        val devOptions = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        val settings = Intent(Settings.ACTION_SETTINGS)
        if (!openFirst(tile, subSettings, devOptions, settings)) vm.note = "Open Settings, then Developer options, then Wireless debugging."
    }

    private fun openDeepLink(vararg candidates: Intent) {
        if (!openFirst(*candidates)) vm.note = "Couldn't open that settings screen on this phone."
    }

    private fun openFirst(vararg candidates: Intent): Boolean {
        for (intent in candidates) {
            if (runCatching { startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun testVoice() {
        thread(name = "pony-test-voice") {
            val ok = SpeechOutput.speak(this, "Nase can hear you.", vm.speechRate, vm.ttsVoice)
            if (!ok) Handler(Looper.getMainLooper()).post { vm.note = "Nase couldn't speak. Check the phone's text-to-speech settings." }
        }
    }

    private fun previewVoice(name: String) {
        thread(name = "pony-preview-voice") {
            val ok = SpeechOutput.speak(this, "Hi, I'm Nase. This is how I'll sound.", vm.speechRate, name)
            if (!ok) Handler(Looper.getMainLooper()).post { vm.note = "That voice needs to be downloaded first. Open your phone's text-to-speech settings to add it." }
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return
        startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
    }

    private fun syncWake() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (vm.wakeWord && vm.voiceEnabled && mic) WakeWordService.start(this) else WakeWordService.stop(this)
    }

    companion object {
        const val EXTRA_REQUEST_MIC = "request_mic"
    }
}

/** Status and navigation bar icons follow Nase's theme, not just the phone's, so they always contrast. */
@Composable
private fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
