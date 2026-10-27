package app.nase.companion.a11y

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {
    fun enabled(context: Context): Boolean {
        val expected = ComponentName(context, NaseAccessibilityService::class.java).flattenToString()
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return setting.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
