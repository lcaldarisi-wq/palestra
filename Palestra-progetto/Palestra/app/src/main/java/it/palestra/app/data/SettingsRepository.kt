package it.palestra.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val defaultRestSec: Int = 90,
    val autoTimer: Boolean = true,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val keepScreenOn: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun load() = Settings(
        defaultRestSec = prefs.getInt("defaultRestSec", 90),
        autoTimer = prefs.getBoolean("autoTimer", true),
        sound = prefs.getBoolean("sound", true),
        vibrate = prefs.getBoolean("vibrate", true),
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_state.value)
        prefs.edit()
            .putInt("defaultRestSec", s.defaultRestSec)
            .putBoolean("autoTimer", s.autoTimer)
            .putBoolean("sound", s.sound)
            .putBoolean("vibrate", s.vibrate)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .apply()
        _state.value = s
    }
}
