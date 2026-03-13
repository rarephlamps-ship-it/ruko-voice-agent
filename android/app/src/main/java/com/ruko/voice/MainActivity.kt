package com.ruko.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ruko.voice.databinding.ActivityMainBinding
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite
import com.twilio.voice.ConnectOptions
import com.twilio.voice.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_CALL_INVITE = "CALL_INVITE"
        const val EXTRA_CANCEL_CALL_INVITE = "CANCEL_CALL_INVITE"
    }

    private lateinit var binding: ActivityMainBinding
    private val httpClient = OkHttpClient()

    private var activeCall: Call? = null
    private var accessToken: String? = null

    // --- Permissions ---

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val denied = results.entries.filter { !it.value }.map { it.key }
            if (denied.isEmpty()) {
                Log.d(TAG, "All permissions granted")
                fetchToken()
            } else {
                updateStatus("Permissions required: ${denied.joinToString()}")
                Toast.makeText(this, "Please grant all required permissions.", Toast.LENGTH_LONG).show()
            }
        }

    // --- Lifecycle ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCall.setOnClickListener { placeOutgoingCall() }
        binding.btnHangup.setOnClickListener { hangUp() }

        updateCallControlsVisibility(onCall = false)
        updateStatus("Initialising…")

        checkAndRequestPermissions()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeCall?.disconnect()
    }

    // --- Permission helpers ---

    private fun checkAndRequestPermissions() {
        val needsRequest = requiredPermissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            fetchToken()
        }
    }

    // --- Intent handling (incoming call accept/reject routed through here) ---

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.hasExtra(EXTRA_CALL_INVITE) -> {
                val callInvite: CallInvite? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_CALL_INVITE, CallInvite::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CALL_INVITE)
                }
                callInvite?.let { startIncomingCallActivity(it) }
            }
        }
    }

    private fun startIncomingCallActivity(callInvite: CallInvite) {
        val incomingIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_INCOMING_CALL_INVITE, callInvite)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(incomingIntent)
    }

    // --- Token fetch ---

    private fun fetchToken() {
        updateStatus("Fetching token…")
        val identity = BuildConfig.TWILIO_IDENTITY
        val url = "${BuildConfig.SERVER_BASE_URL}/token?identity=$identity"
        lifecycleScope.launch {
            try {
                val body = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}")
                    }
                    response.body?.string() ?: throw Exception("Empty response")
                }
                val json = JSONObject(body)
                accessToken = json.getString("token")
                TokenStore.saveToken(applicationContext, accessToken!!)
                updateStatus("Ready. Identity: $identity")
                binding.btnCall.isEnabled = true
                Log.d(TAG, "Token fetched for identity: $identity")
            } catch (e: Exception) {
                Log.e(TAG, "Token fetch error", e)
                updateStatus("Token fetch error: ${e.message}")
            }
        }
    }

    // --- Call control ---

    private fun placeOutgoingCall() {
        val token = accessToken
        if (token == null) {
            Toast.makeText(this, "Not ready — token not available", Toast.LENGTH_SHORT).show()
            return
        }
        updateStatus("Connecting…")
        val params = mapOf("To" to BuildConfig.TWILIO_IDENTITY)
        val connectOptions = ConnectOptions.Builder(token).params(params).build()
        activeCall = Voice.connect(this, connectOptions, callListener)
        updateCallControlsVisibility(onCall = true)
    }

    private fun hangUp() {
        activeCall?.disconnect()
    }

    // --- Twilio Call.Listener ---

    private val callListener = object : Call.Listener {
        override fun onConnectFailure(call: Call, error: CallException) {
            Log.e(TAG, "Connect failure: ${error.message}")
            runOnUiThread {
                updateStatus("Connection failed: ${error.message}")
                updateCallControlsVisibility(onCall = false)
            }
            activeCall = null
        }

        override fun onRinging(call: Call) {
            runOnUiThread { updateStatus("Ringing…") }
        }

        override fun onConnected(call: Call) {
            Log.d(TAG, "Connected")
            activeCall = call
            runOnUiThread {
                updateStatus("On call")
                updateCallControlsVisibility(onCall = true)
            }
        }

        override fun onReconnecting(call: Call, error: CallException) {
            runOnUiThread { updateStatus("Reconnecting…") }
        }

        override fun onReconnected(call: Call) {
            runOnUiThread { updateStatus("On call (reconnected)") }
        }

        override fun onDisconnected(call: Call, error: CallException?) {
            Log.d(TAG, "Disconnected")
            activeCall = null
            runOnUiThread {
                updateStatus(if (error != null) "Disconnected: ${error.message}" else "Call ended")
                updateCallControlsVisibility(onCall = false)
            }
        }
    }

    // --- UI helpers ---

    private fun updateStatus(message: String) {
        binding.tvStatus.text = message
        Log.d(TAG, "Status: $message")
    }

    private fun updateCallControlsVisibility(onCall: Boolean) {
        binding.btnCall.isEnabled = !onCall && accessToken != null
        binding.btnHangup.isEnabled = onCall
    }
}
