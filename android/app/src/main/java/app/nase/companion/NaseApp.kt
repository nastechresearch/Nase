package app.nase.companion

import android.app.Application
import app.nase.companion.schedule.ScheduleAlarms
import app.nase.companion.session.Readiness
import app.nase.companion.tasks.TaskRuntime
import app.nase.companion.voice.VoiceController

class NaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskRuntime.init(this)
        VoiceController.init(this)
        Readiness.watch(this)
        runCatching { ScheduleAlarms.rescheduleAll(this) }
    }
}
