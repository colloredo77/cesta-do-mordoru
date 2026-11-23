package cz.rodina.mordor

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Každý večer kolem 22:00 nahraje kroky z Health Connect, případně připomene, ať to člověk udělá. */
object DailySync {
    private const val WORK = "daily-steps-22"
    const val LOW_STEPS = 1500L

    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(22, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<DailySyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class DailySyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("mordor", Context.MODE_PRIVATE)
        val code = prefs.getString("family", null) ?: return Result.success()
        val id = prefs.getString("member", null) ?: return Result.success()
        val health = Health(ctx)
        var todaySteps: Long? = null
        try {
            if (health.available() && health.hasPermission() && health.hasBackgroundPermission()) {
                val repo = Repo()
                repo.ensureAuth()
                val today = LocalDate.now()
                val steps = health.dailySteps(today.minusDays(6), today)
                repo.setHealthSteps(code, id, steps)
                todaySteps = steps[today.toString()] ?: 0L
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d. M. HH:mm"))
                prefs.edit().putString("lastSync", stamp).apply()
            }
        } catch (_: Exception) {
            todaySteps = null
        }
        when {
            todaySteps == null -> Notifier.show(
                ctx,
                "Nahraj dnešní kroky",
                "Otevři Cestu do Mordoru, ať se kroky započítají dřív, než se nepřátelé v noci pohnou.",
            )
            todaySteps < DailySync.LOW_STEPS -> Notifier.show(
                ctx,
                "Dnes jen ${todaySteps.fmt()} kroků?",
                "Možná hodinky ještě nesynchronizovaly. Otevři Garmin Connect / Mi Fitness a pak Cestu do Mordoru.",
            )
        }
        return Result.success()
    }
}

object Notifier {
    private const val CHANNEL = "vyprava"

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, title: String, text: String) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Výprava", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(22, n)
    }
}
