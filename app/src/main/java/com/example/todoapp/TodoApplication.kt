package com.example.todoapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.todoapp.notification.InsightNotificationScheduler
import com.example.todoapp.notification.InsightNotificationWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TodoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var scheduler: InsightNotificationScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduler.schedule()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            InsightNotificationWorker.CHANNEL_ID,
            "Weekly insight",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Weekly productivity summary delivered every Sunday at 6 PM"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
