package it.sergio.scadenze

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

/** Avvisi di scadenza: la pagina salva qui i suoi dati, il telefono li controlla ogni mattina. */
object Reminders {
    private const val CHANNEL = "scadenze"
    private const val PREFS = "scadenze"
    private const val HOUR = 9

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveData(c: Context, json: String) {
        prefs(c).edit().putString("data", json).apply()
    }

    fun canNotify(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(c).areNotificationsEnabled()
    }

    fun notify(c: Context, title: String, body: String) {
        if (!canNotify(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Scadenze in arrivo", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2E6B4E.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(1, n)
        } catch (e: SecurityException) {
        }
    }

    /** Un controllo al giorno, verso le 9. Inesatto di proposito: consuma meno batteria. */
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            c, 0, Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    /** Stesso criterio della pagina: prodotti che scadono tra oggi e i giorni di preavviso scelti. */
    fun check(c: Context) {
        val p = prefs(c)
        val today = LocalDate.now()
        if (p.getString("lastCheck", "") == today.toString()) return
        val json = p.getString("data", null) ?: return
        val items = try { JSONObject(json).optJSONArray("items") } catch (e: Exception) { null } ?: return
        data class Soon(val name: String, val d: Long, val expiry: LocalDate)
        val soon = mutableListOf<Soon>()
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            val expiry = try { LocalDate.parse(it.optString("expiry")) } catch (e: Exception) { continue }
            val d = ChronoUnit.DAYS.between(today, expiry)
            val remind = if (it.has("remind") && !it.isNull("remind")) it.optInt("remind", 3) else 3
            if (d in 0..remind.toLong()) soon += Soon(it.optString("name"), d, expiry)
        }
        p.edit().putString("lastCheck", today.toString()).apply()
        if (soon.isEmpty()) return
        soon.sortBy { it.d }
        val title = if (soon.size == 1) "${soon[0].name} scade ${rel(soon[0].d)}"
        else "${soon.size} prodotti stanno per scadere"
        val body = if (soon.size == 1)
            "Scadenza ${soon[0].expiry.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN))}. Meglio consumarlo presto."
        else soon.take(3).joinToString(", ") { "${it.name} ${rel(it.d)}" } + if (soon.size > 3) "…" else "."
        notify(c, title, body)
    }

    private fun rel(d: Long) = when (d) {
        0L -> "oggi"
        1L -> "domani"
        else -> "tra $d giorni"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.check(context)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.schedule(context)
    }
}
