package com.ruko.voiceagent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {

    companion object {
        const val INCOMING_CALL_CHANNEL_ID = "incoming_call"
        const val INCOMING_CALL_CHANNEL_NAME = "Incoming Calls"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val incomingCallChannel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                INCOMING_CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming voice calls"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(incomingCallChannel)
        }
    }
}
