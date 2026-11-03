package it.palestra.app.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import it.palestra.app.MainActivity
import it.palestra.app.PalestraApp
import it.palestra.app.R
import it.palestra.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimerState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val totalSec: Int = 0,
    /** Istante di fine in SystemClock.elapsedRealtime() (non risente di cambi d'ora). */
    val endAtElapsed: Long = 0L,
    val pausedRemainingMs: Long = 0L,
    val label: String = "",
) {
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long = when {
        !active -> 0L
        paused -> pausedRemainingMs
        else -> (endAtElapsed - now).coerceAtLeast(0L)
    }
}

/**
 * Timer di recupero.
 *
 * Scelta tecnica: il timer non "conta" in memoria. Salva l'istante di fine e programma un allarme
 * esatto di sistema (AlarmManager, anche in Doze). In questo modo funziona con schermo spento,
 * app in background o anche chiusa da Android: allo scadere il sistema risveglia l'app che emette
 * suono e vibrazione. Una notifica con conto alla rovescia lo rende visibile ovunque.
 */
class RestTimerController(private val context: Context, private val settings: SettingsRepository) {

    private val prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    init {
        TimerNotifications.createChannels(context)
        val s = _state.value
        if (s.active && !s.paused && s.remainingMs() <= 0L) finish(alert = false)
    }

    fun start(seconds: Int, label: String) {
        if (seconds <= 0) return
        val s = TimerState(
            active = true,
            totalSec = seconds,
            endAtElapsed = SystemClock.elapsedRealtime() + seconds * 1000L,
            label = label,
        )
        set(s)
        scheduleAlarm(s.endAtElapsed)
        TimerNotifications.showRunning(context, s)
    }

    fun pause() {
        val s = _state.value
        if (!s.active || s.paused) return
        cancelAlarm()
        val n = s.copy(paused = true, pausedRemainingMs = s.remainingMs())
        set(n)
        TimerNotifications.showRunning(context, n)
    }

    fun resume() {
        val s = _state.value
        if (!s.active || !s.paused) return
        val n = s.copy(paused = false, endAtElapsed = SystemClock.elapsedRealtime() + s.pausedRemainingMs)
        set(n)
        scheduleAlarm(n.endAtElapsed)
        TimerNotifications.showRunning(context, n)
    }

    fun togglePause() = if (_state.value.paused) resume() else pause()

    fun addSeconds(delta: Int) {
        val s = _state.value
        if (!s.active) return
        val newRemaining = s.remainingMs() + delta * 1000L
        if (newRemaining <= 0L) {
            finish(alert = false); return
        }
        val n = if (s.paused) {
            s.copy(pausedRemainingMs = newRemaining, totalSec = (s.totalSec + delta).coerceAtLeast(1))
        } else {
            s.copy(endAtElapsed = SystemClock.elapsedRealtime() + newRemaining, totalSec = (s.totalSec + delta).coerceAtLeast(1))
        }
        set(n)
        if (!n.paused) scheduleAlarm(n.endAtElapsed)
        TimerNotifications.showRunning(context, n)
    }

    fun skip() {
        cancelAlarm()
        TimerNotifications.cancelRunning(context)
        set(TimerState())
    }

    /** Chiamato dall'allarme di sistema allo scadere. */
    fun onAlarm() {
        val s = _state.value
        if (!s.active || s.paused) return
        val remaining = s.remainingMs()
        if (remaining > 1500L) {
            scheduleAlarm(s.endAtElapsed) // allarme arrivato in anticipo: riprogramma
        } else {
            finish(alert = true)
        }
    }

    private fun finish(alert: Boolean) {
        val label = _state.value.label
        cancelAlarm()
        TimerNotifications.cancelRunning(context)
        set(TimerState())
        if (alert) {
            val st = settings.state.value
            TimerNotifications.showFinished(context, label)
            if (st.sound) playSound()
            if (st.vibrate) vibrate()
        }
    }

    private fun set(s: TimerState) {
        _state.value = s
        prefs.edit()
            .putBoolean("active", s.active)
            .putBoolean("paused", s.paused)
            .putInt("totalSec", s.totalSec)
            .putLong("endAtElapsed", s.endAtElapsed)
            .putLong("pausedRemainingMs", s.pausedRemainingMs)
            .putString("label", s.label)
            .apply()
    }

    private fun load(): TimerState {
        val s = TimerState(
            active = prefs.getBoolean("active", false),
            paused = prefs.getBoolean("paused", false),
            totalSec = prefs.getInt("totalSec", 0),
            endAtElapsed = prefs.getLong("endAtElapsed", 0L),
            pausedRemainingMs = prefs.getLong("pausedRemainingMs", 0L),
            label = prefs.getString("label", "") ?: "",
        )
        // Dopo un riavvio del telefono elapsedRealtime riparte da zero: il timer non è più valido.
        if (s.active && !s.paused && s.endAtElapsed - SystemClock.elapsedRealtime() > s.totalSec * 1000L + 5000L) {
            return TimerState()
        }
        return s
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 100,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_FINISH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAlarm(endAtElapsed: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent()
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAtElapsed, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAtElapsed, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAtElapsed, pi)
        }
    }

    private fun cancelAlarm() {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent())
    }

    private fun playSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (e: Exception) {
        }
    }

    private fun vibrate() {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350, 150, 600), -1))
        } catch (e: Exception) {
        }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = (context.applicationContext as PalestraApp).container.timer
        when (intent.action) {
            ACTION_FINISH -> timer.onAlarm()
            ACTION_PAUSE -> timer.pause()
            ACTION_RESUME -> timer.resume()
            ACTION_ADD -> timer.addSeconds(15)
            ACTION_SKIP -> timer.skip()
        }
    }

    companion object {
        const val ACTION_FINISH = "it.palestra.app.timer.FINISH"
        const val ACTION_PAUSE = "it.palestra.app.timer.PAUSE"
        const val ACTION_RESUME = "it.palestra.app.timer.RESUME"
        const val ACTION_ADD = "it.palestra.app.timer.ADD"
        const val ACTION_SKIP = "it.palestra.app.timer.SKIP"
    }
}

object TimerNotifications {
    private const val CH_RUNNING = "timer_running"
    private const val CH_DONE = "timer_done"
    private const val ID_RUNNING = 1001
    private const val ID_DONE = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, "Timer di recupero", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Conto alla rovescia del recupero in corso"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Fine recupero", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avviso quando il recupero è terminato"
                // suono e vibrazione sono gestiti dall'app secondo le impostazioni
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun fmt(ms: Long): String {
        val s = ((ms + 999) / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    fun showRunning(context: Context, state: TimerState) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return
        val remaining = state.remainingMs()
        val b = NotificationCompat.Builder(context, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(context))
        if (state.paused) {
            b.setContentTitle("Recupero in pausa · ${fmt(remaining)}")
                .setContentText(state.label)
                .setShowWhen(false)
                .addAction(0, "Riprendi", action(context, TimerReceiver.ACTION_RESUME, 1))
        } else {
            b.setContentTitle("Recupero")
                .setContentText(state.label)
                .setWhen(System.currentTimeMillis() + remaining)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(0, "Pausa", action(context, TimerReceiver.ACTION_PAUSE, 2))
        }
        b.addAction(0, "+15 s", action(context, TimerReceiver.ACTION_ADD, 3))
            .addAction(0, "Salta", action(context, TimerReceiver.ACTION_SKIP, 4))
        try {
            nm.notify(ID_RUNNING, b.build())
        } catch (e: SecurityException) {
        }
    }

    fun cancelRunning(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_RUNNING)
    }

    fun showFinished(context: Context, label: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Recupero terminato")
            .setContentText(if (label.isBlank()) "Pronto per la prossima serie" else "Prossima serie · $label")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .setContentIntent(openApp(context))
            .build()
        try {
            nm.notify(ID_DONE, n)
        } catch (e: SecurityException) {
        }
    }
}
