package com.ruko.voiceagent

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.MessageListener
import com.twilio.voice.Voice

/**
 * Handles incoming FCM messages from Twilio and translates them into
 * [CallInvite] objects via the Twilio Voice SDK.
 *
 * When a push notification arrives:
 *  1. [Voice.handleMessage] parses the FCM data.
 *  2. [MessageListener.onCallInvite] fires with the [CallInvite].
 *  3. A heads-up notification is shown; tapping it brings [MainActivity] to the
 *     foreground where the user can Accept or Reject.
 */
class RukoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val INCOMING_CALL_NOTIFICATION_ID = 1
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FCM callbacks
    // ──────────────────────────────────────────────────────────────────────────

    /** Called when a new FCM token is generated (first launch or token refresh). */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        TokenStore.saveFcmToken(applicationContext, token)

        val accessToken = TokenStore.getAccessToken(applicationContext)
        if (!accessToken.isNullOrEmpty()) {
            Voice.register(accessToken, Voice.RegistrationChannel.FCM, token, object : com.twilio.voice.RegistrationListener {
                override fun onRegistered(accessToken: String, fcmToken: String) {}
                override fun onError(registrationException: com.twilio.voice.RegistrationException, accessToken: String, fcmToken: String) {}
            })
        }
    }

    /** Called for every incoming FCM data message — Twilio incoming calls arrive here. */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val isValid = Voice.handleMessage(this, remoteMessage.data, messageListener)
        if (!isValid) {
            // Not a Twilio Voice message — handle other FCM messages here if needed.
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Twilio MessageListener
    // ──────────────────────────────────────────────────────────────────────────

    private val messageListener = object : MessageListener {
        override fun onCallInvite(callInvite: CallInvite) {
            CallManager.onIncomingCallInvite(callInvite)
            showIncomingCallNotification(callInvite)
        }

        override fun onCancelledCallInvite(
            cancelledCallInvite: CancelledCallInvite,
            callException: com.twilio.voice.CallException?
        ) {
            CallManager.onIncomingCallCancelled()
            cancelIncomingCallNotification()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notifications
    // ──────────────────────────────────────────────────────────────────────────

    private fun showIncomingCallNotification(callInvite: CallInvite) {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(this, 0, contentIntent, pendingFlags)

        val caller = callInvite.from ?: "Unknown caller"
        val notification = NotificationCompat.Builder(this, App.INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming call")
            .setContentText(caller)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            nm.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
        }
    }

    private fun cancelIncomingCallNotification() {
        NotificationManagerCompat.from(this).cancel(INCOMING_CALL_NOTIFICATION_ID)
    }
}
