package com.ruko.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.MessageListener
import com.twilio.voice.Voice

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "RukoFCM"
        private const val INCOMING_CALL_CHANNEL_ID = "incoming_call"
        private const val INCOMING_CALL_NOTIFICATION_ID = 1
    }

    /**
     * Called when a new FCM registration token is generated (on first run or after token refresh).
     * Register the token with Twilio Voice so the backend can route push notifications here.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        registerWithTwilio(token)
    }

    /**
     * Called when an FCM message arrives. Twilio Voice sends push notifications as data-only
     * messages; forward them to the Voice SDK for processing.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        if (remoteMessage.data.isEmpty()) {
            Log.d(TAG, "Non-Twilio FCM message — skipping")
            return
        }

        // Let Twilio Voice inspect the payload
        Voice.handleMessage(applicationContext, remoteMessage.data, object : MessageListener {
            override fun onCallInvite(callInvite: CallInvite) {
                Log.d(TAG, "Incoming call from: ${callInvite.from}")
                showIncomingCallNotification(callInvite)
            }

            override fun onCancelledCallInvite(
                cancelledCallInvite: CancelledCallInvite,
                callInvite: CallInvite?
            ) {
                Log.d(TAG, "Cancelled call invite from: ${cancelledCallInvite.from}")
                cancelIncomingCallNotification()
            }
        })
    }

    // --- Twilio registration ---

    private fun registerWithTwilio(fcmToken: String) {
        // Access token must be fetched from the backend first.
        // TokenStore is a simple SharedPreferences helper that caches the last known JWT.
        val accessToken = TokenStore.getToken(applicationContext)
        if (accessToken == null) {
            Log.w(TAG, "No access token cached — skip Twilio registration for now. " +
                    "MainActivity will register after fetching a fresh token.")
            return
        }
        Voice.register(
            accessToken,
            Voice.RegistrationChannel.FCM,
            fcmToken,
            registrationListener
        )
    }

    private val registrationListener = object : com.twilio.voice.RegistrationListener {
        override fun onRegistered(accessToken: String, fcmToken: String) {
            Log.d(TAG, "Twilio registered. FCM: $fcmToken")
        }

        override fun onError(registrationException: com.twilio.voice.RegistrationException, accessToken: String, fcmToken: String) {
            Log.e(TAG, "Twilio registration error: ${registrationException.message}")
        }
    }

    // --- Incoming call notification ---

    private fun showIncomingCallNotification(callInvite: CallInvite) {
        createNotificationChannel()

        val answerIntent = Intent(applicationContext, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_INCOMING_CALL_INVITE, callInvite)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Call")
            .setContentText("Call from: ${callInvite.from ?: "Unknown"}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(answerPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Answer",
                answerPendingIntent
            )
            .build()

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
    }

    private fun cancelIncomingCallNotification() {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming Twilio Voice calls"
            }
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
