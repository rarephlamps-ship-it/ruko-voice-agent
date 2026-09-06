package com.ruko.voiceagent

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.ruko.voiceagent.databinding.ActivityMainBinding
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite
import com.twilio.voice.ConnectOptions
import com.twilio.voice.RegistrationException
import com.twilio.voice.RegistrationListener
import com.twilio.voice.UnregistrationListener
import com.twilio.voice.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : AppCompatActivity(), CallManager.CallManagerListener {

    private lateinit var binding: ActivityMainBinding

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var accessToken: String? = null
    private var fcmToken: String? = null

    // Runtime-permission request codes
    private val RC_PERMISSIONS = 101

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        CallManager.setListener(this)
    }

    override fun onPause() {
        super.onPause()
        CallManager.setListener(null)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Permissions
    // ──────────────────────────────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.POST_NOTIFICATIONS)

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), RC_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_PERMISSIONS) {
            val denied = permissions.filterIndexed { i, _ ->
                grantResults[i] != PackageManager.PERMISSION_GRANTED
            }
            if (denied.isNotEmpty()) {
                setStatus("Permission denied: ${denied.joinToString()}")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Button wiring
    // ──────────────────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnRegister.setOnClickListener { fetchTokenAndRegister() }
        binding.btnUnregister.setOnClickListener { unregister() }
        binding.btnCall.setOnClickListener { placeCall() }
        binding.btnHangup.setOnClickListener { hangUp() }
        binding.btnAccept.setOnClickListener { acceptIncomingCall() }
        binding.btnReject.setOnClickListener { rejectIncomingCall() }

        showIncomingCallButtons(false)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Token fetch + registration
    // ──────────────────────────────────────────────────────────────────────────

    private fun fetchTokenAndRegister() {
        setStatus("Fetching access token…")
        scope.launch {
            val token = fetchAccessToken()
            if (token == null) {
                setStatus("Failed to fetch token. Is the backend running?")
                return@launch
            }
            accessToken = token
            setStatus("Token received. Fetching FCM token…")
            getFcmTokenAndRegister()
        }
    }

    /**
     * Fetches the Twilio access token from the backend /token endpoint.
     * Change [BuildConfig.TOKEN_SERVER_URL] in app/build.gradle (or supply a
     * custom identity via a build-config field) to point to your hosted server.
     */
    private suspend fun fetchAccessToken(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "${BuildConfig.TOKEN_SERVER_URL}/token?identity=ruko-user"
            val response = URL(url).readText()
            // Response JSON: {"token":"<jwt>","identity":"<id>"}
            val tokenStart = response.indexOf("\"token\":\"") + 9
            val tokenEnd = response.indexOf("\"", tokenStart)
            if (tokenStart > 8 && tokenEnd > tokenStart) response.substring(tokenStart, tokenEnd)
            else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getFcmTokenAndRegister() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                setStatus("FCM token fetch failed: ${task.exception?.message}")
                return@addOnCompleteListener
            }
            fcmToken = task.result
            setStatus("Registering with Twilio…")
            registerWithTwilio()
        }
    }

    private fun registerWithTwilio() {
        val token = accessToken ?: run { setStatus("No access token"); return }
        val fcm = fcmToken ?: run { setStatus("No FCM token"); return }

        Voice.register(token, Voice.RegistrationChannel.FCM, fcm, object : RegistrationListener {
            override fun onRegistered(accessToken: String, fcmToken: String) {
                setStatus("Registered — ready for calls")
                binding.btnRegister.isEnabled = false
                binding.btnUnregister.isEnabled = true
            }

            override fun onError(
                registrationException: RegistrationException,
                accessToken: String,
                fcmToken: String
            ) {
                setStatus("Registration failed: ${registrationException.message}")
            }
        })
    }

    private fun unregister() {
        val token = accessToken ?: run { setStatus("Not registered"); return }
        val fcm = fcmToken ?: run { setStatus("No FCM token"); return }

        Voice.unregister(token, Voice.RegistrationChannel.FCM, fcm, object : UnregistrationListener {
            override fun onUnregistered(accessToken: String, fcmToken: String) {
                setStatus("Unregistered")
                binding.btnRegister.isEnabled = true
                binding.btnUnregister.isEnabled = false
            }

            override fun onError(
                registrationException: RegistrationException,
                accessToken: String,
                fcmToken: String
            ) {
                setStatus("Unregistration failed: ${registrationException.message}")
            }
        })
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Outgoing call
    // ──────────────────────────────────────────────────────────────────────────

    private fun placeCall() {
        val token = accessToken ?: run {
            Toast.makeText(this, "Register first", Toast.LENGTH_SHORT).show()
            return
        }
        val to = binding.etTo.text.toString().trim()
        if (to.isEmpty()) {
            Toast.makeText(this, "Enter a callee identity or phone number", Toast.LENGTH_SHORT).show()
            return
        }

        val params = mapOf("To" to to)
        val connectOptions = ConnectOptions.Builder(token).params(params).build()
        CallManager.activeCall = Voice.connect(this, connectOptions, callListener)
        setCallState("Calling $to…")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Incoming call (from CallManager / FCM)
    // ──────────────────────────────────────────────────────────────────────────

    override fun onIncomingCall(callInvite: CallInvite) {
        setCallState("Incoming call from ${callInvite.from ?: "unknown"}")
        showIncomingCallButtons(true)
    }

    override fun onIncomingCallCancelled() {
        setCallState("Incoming call cancelled")
        showIncomingCallButtons(false)
    }

    private fun acceptIncomingCall() {
        val invite = CallManager.activeCallInvite ?: return
        CallManager.activeCall = invite.accept(this, callListener)
        CallManager.activeCallInvite = null
        showIncomingCallButtons(false)
        setCallState("Call connected")
        dismissIncomingCallNotification()
    }

    private fun rejectIncomingCall() {
        CallManager.activeCallInvite?.reject(this)
        CallManager.activeCallInvite = null
        showIncomingCallButtons(false)
        setCallState("Call rejected")
        dismissIncomingCallNotification()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Hang up
    // ──────────────────────────────────────────────────────────────────────────

    private fun hangUp() {
        CallManager.activeCall?.disconnect()
        CallManager.activeCall = null
        setCallState("Idle")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Call.Listener
    // ──────────────────────────────────────────────────────────────────────────

    private val callListener = object : Call.Listener {
        override fun onConnectFailure(call: Call, callException: CallException) {
            setCallState("Connect failed: ${callException.message}")
            CallManager.activeCall = null
        }

        override fun onRinging(call: Call) {
            setCallState("Ringing…")
        }

        override fun onConnected(call: Call) {
            setCallState("Connected")
        }

        override fun onReconnecting(call: Call, callException: CallException) {
            setCallState("Reconnecting…")
        }

        override fun onReconnected(call: Call) {
            setCallState("Reconnected")
        }

        override fun onDisconnected(call: Call, callException: CallException?) {
            setCallState(if (callException != null) "Disconnected: ${callException.message}" else "Disconnected")
            CallManager.activeCall = null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun dismissIncomingCallNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(RukoFirebaseMessagingService.INCOMING_CALL_NOTIFICATION_ID)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun setStatus(msg: String) {
        binding.tvStatus.text = msg
    }

    private fun setCallState(msg: String) {
        binding.tvCallState.text = msg
    }

    private fun showIncomingCallButtons(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        binding.btnAccept.visibility = visibility
        binding.btnReject.visibility = visibility
    }
}
