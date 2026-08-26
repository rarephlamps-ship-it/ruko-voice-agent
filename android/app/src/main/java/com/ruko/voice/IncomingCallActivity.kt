package com.ruko.voice

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.ruko.voice.databinding.ActivityIncomingCallBinding
import com.twilio.voice.AcceptOptions
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite

class IncomingCallActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IncomingCallActivity"
        const val EXTRA_INCOMING_CALL_INVITE = "INCOMING_CALL_INVITE"
    }

    private lateinit var binding: ActivityIncomingCallBinding
    private var callInvite: CallInvite? = null
    private var activeCall: Call? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        callInvite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_INCOMING_CALL_INVITE, CallInvite::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_INCOMING_CALL_INVITE)
        }

        val from = callInvite?.from ?: "Unknown"
        binding.tvCallerName.text = from
        binding.tvIncomingStatus.text = "Incoming call from $from"

        binding.btnAccept.setOnClickListener { acceptCall() }
        binding.btnReject.setOnClickListener { rejectCall() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callInvite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_INCOMING_CALL_INVITE, CallInvite::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_INCOMING_CALL_INVITE)
        }
    }

    private fun acceptCall() {
        val invite = callInvite ?: return
        Log.d(TAG, "Accepting call")
        cancelIncomingCallNotification()
        val acceptOptions = AcceptOptions.Builder().build()
        activeCall = invite.accept(this, acceptOptions, callListener)
        binding.tvIncomingStatus.text = "Connected"
        binding.btnAccept.isEnabled = false
        binding.btnReject.text = "Hang up"
    }

    private fun rejectCall() {
        val invite = callInvite
        val call = activeCall
        when {
            call != null -> {
                Log.d(TAG, "Hanging up active call")
                call.disconnect()
            }
            invite != null -> {
                Log.d(TAG, "Rejecting call invite")
                invite.reject(this)
                cancelIncomingCallNotification()
                finish()
            }
            else -> finish()
        }
    }

    private val callListener = object : Call.Listener {
        override fun onConnectFailure(call: Call, error: CallException) {
            Log.e(TAG, "Connect failure: ${error.message}")
            runOnUiThread {
                binding.tvIncomingStatus.text = "Connection failed: ${error.message}"
                finish()
            }
        }

        override fun onRinging(call: Call) {
            runOnUiThread { binding.tvIncomingStatus.text = "Ringing…" }
        }

        override fun onConnected(call: Call) {
            Log.d(TAG, "Connected")
            activeCall = call
            runOnUiThread { binding.tvIncomingStatus.text = "On call" }
        }

        override fun onReconnecting(call: Call, error: CallException) {
            runOnUiThread { binding.tvIncomingStatus.text = "Reconnecting…" }
        }

        override fun onReconnected(call: Call) {
            runOnUiThread { binding.tvIncomingStatus.text = "On call (reconnected)" }
        }

        override fun onDisconnected(call: Call, error: CallException?) {
            Log.d(TAG, "Disconnected")
            activeCall = null
            runOnUiThread {
                binding.tvIncomingStatus.text = if (error != null) "Disconnected: ${error.message}" else "Call ended"
                finish()
            }
        }
    }

    private fun cancelIncomingCallNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1) // INCOMING_CALL_NOTIFICATION_ID from MyFirebaseMessagingService
    }
}
