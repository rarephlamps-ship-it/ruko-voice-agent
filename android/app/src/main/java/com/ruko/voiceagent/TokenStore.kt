package com.ruko.voiceagent

import android.content.Context
import android.content.SharedPreferences

object TokenStore {
    private const val PREFS_NAME = "ruko_voice_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAccessToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun saveFcmToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getFcmToken(context: Context): String? =
        prefs(context).getString(KEY_FCM_TOKEN, null)
}
