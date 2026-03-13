package com.ruko.voiceagent

import com.twilio.voice.Call
import com.twilio.voice.CallInvite

/**
 * Singleton that holds the current call state and notifies a registered listener.
 * Used to bridge [RukoFirebaseMessagingService] → [MainActivity] without requiring
 * the UI to be running when the FCM message arrives.
 */
object CallManager {

    var activeCall: Call? = null
    var activeCallInvite: CallInvite? = null

    private var listener: CallManagerListener? = null

    fun setListener(l: CallManagerListener?) {
        listener = l
        // If an invite is already waiting, deliver it immediately.
        activeCallInvite?.let { l?.onIncomingCall(it) }
    }

    fun onIncomingCallInvite(invite: CallInvite) {
        activeCallInvite = invite
        listener?.onIncomingCall(invite)
    }

    fun onIncomingCallCancelled() {
        activeCallInvite = null
        listener?.onIncomingCallCancelled()
    }

    interface CallManagerListener {
        fun onIncomingCall(callInvite: CallInvite)
        fun onIncomingCallCancelled()
    }
}
