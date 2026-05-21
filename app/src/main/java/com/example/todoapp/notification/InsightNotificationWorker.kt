package com.example.todoapp.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.todoapp.MainActivity
import com.example.todoapp.R
import com.example.todoapp.data.PreferencesRepository
import com.example.todoapp.data.TodoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@HiltWorker
class InsightNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TodoRepository,
    private val prefsRepository: PreferencesRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "weekly_insight"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        if (!prefsRepository.weeklyInsightEnabled.first()) return Result.success()

        val history = repository.getAllHistory().first()
        val distinctDays = history.map { it.date }.distinct()
        if (distinctDays.size < 7) return Result.success()

        val today = LocalDate.now()
        // "Last week" = the 7 days ending yesterday (Sat when fired on Sun).
        val periodEnd = today.minusDays(1)
        val periodStart = periodEnd.minusDays(6)
        val priorEnd = periodStart.minusDays(1)
        val priorStart = priorEnd.minusDays(6)

        val thisWeek = history.filter {
            val d = LocalDate.parse(it.date)
            !d.isBefore(periodStart) && !d.isAfter(periodEnd)
        }
        val priorWeek = history.filter {
            val d = LocalDate.parse(it.date)
            !d.isBefore(priorStart) && !d.isAfter(priorEnd)
        }

        if (thisWeek.isEmpty()) return Result.success()

        val currentRate = thisWeek.count { it.isCompleted }.toFloat() / thisWeek.size
        val priorRate = if (priorWeek.isNotEmpty())
            priorWeek.count { it.isCompleted }.toFloat() / priorWeek.size
        else null

        val bestDay = bestDayOf(thisWeek.groupBy { LocalDate.parse(it.date).dayOfWeek })
        val priorBestDay = if (priorWeek.isNotEmpty())
            bestDayOf(priorWeek.groupBy { LocalDate.parse(it.date).dayOfWeek })
        else null

        // Skip if no meaningful change week-over-week.
        if (priorRate != null) {
            val rateChange = abs(currentRate - priorRate)
            if (rateChange < 0.05f && bestDay == priorBestDay) return Result.success()
        }

        val pct = (currentRate * 100).roundToInt()
        val priorPct = priorRate?.let { (it * 100).roundToInt() }
        val dayName = bestDay?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "today"
        val rateImprovement = if (priorRate != null) currentRate - priorRate else 0f

        val body = when {
            priorRate != null && rateImprovement >= 0.10f ->
                "You improved! Last week you completed $pct% of your tasks, up from $priorPct% the week before."
            priorRate != null && bestDay != priorBestDay && bestDay != null ->
                "Last week you completed $pct% of your tasks. $dayName was your most productive day."
            else ->
                "Last week you completed $pct% of your tasks. Your best day was $dayName."
        }

        postNotification(body)
        return Result.success()
    }

    private fun bestDayOf(byDow: Map<DayOfWeek, List<com.example.todoapp.data.TaskHistory>>): DayOfWeek? =
        byDow.maxByOrNull { (_, tasks) ->
            tasks.count { it.isCompleted }.toFloat() / tasks.size
        }?.key

    private fun postNotification(body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DASHBOARD, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Weekly insight")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
