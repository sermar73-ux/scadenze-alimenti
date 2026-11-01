package it.sergio.scadenze

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Timer della cucina: suona anche a schermo spento o con l'app chiusa. */
object CookTimer {
    private const val CHANNEL = "cucina_timer"

    private fun open(c: Context) = PendingIntent.getActivity(
        c, 0,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun pending(c: Context, id: Int, label: String) = PendingIntent.getBroadcast(
        c, id,
        Intent(c, CookTimerReceiver::class.java).putExtra("id", id).putExtra("label", label),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun start(c: Context, id: Int, seconds: Int, label: String) {
        val am = c.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + seconds * 1000L
        val pi = pending(c, id, label)
        // Come una sveglia: parte puntuale anche con il telefono in riposo
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, open(c)), pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(c: Context, id: Int) {
        c.getSystemService(AlarmManager::class.java).cancel(pending(c, id, ""))
        NotificationManagerCompat.from(c).cancel(id)
    }

    fun ring(c: Context, id: Int, label: String) {
        if (!Reminders.canNotify(c)) return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Timer della cucina", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            }
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB4532A.toInt())
            .setContentTitle("Tempo scaduto")
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open(c))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(id, n)
        } catch (e: SecurityException) {
        }
    }
}

class CookTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CookTimer.ring(context, intent.getIntExtra("id", 100), intent.getStringExtra("label") ?: "")
    }
}
