package com.ruko.voice

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight SharedPreferences wrapper used to persist the last known Twilio access token
 * so the FCM service can register/unregister with Voice without launching MainActivity.
 */
object TokenStore {

    private const val PREFS_NAME = "ruko_voice_prefs"
    private const val KEY_TOKEN = "access_token"

    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun clearToken(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
