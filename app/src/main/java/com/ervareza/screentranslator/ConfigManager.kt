package com.ervareza.screentranslator

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ScreenTranslatorPrefs", Context.MODE_PRIVATE)

    var inactivityDelayMs: Long
        get() = prefs.getLong("inactivityDelayMs", 3000L) // default 3 seconds
        set(value) = prefs.edit().putLong("inactivityDelayMs", value).apply()

    var targetLanguage: String
        get() = prefs.getString("targetLanguage", "id") ?: "id" // default Indonesian
        set(value) = prefs.edit().putString("targetLanguage", value).apply()

    var sourceLanguage: String
        get() = prefs.getString("sourceLanguage", "auto") ?: "auto" // default Auto-Detect
        set(value) = prefs.edit().putString("sourceLanguage", value).apply()

    var overlayOpacity: Int
        get() = prefs.getInt("overlayOpacity", 200) // 0-255, default ~78% solid
        set(value) = prefs.edit().putInt("overlayOpacity", value).apply()
        
    var placementMode: String
        get() = prefs.getString("placementMode", "direct") ?: "direct" // "direct", "left", "right"
        set(value) = prefs.edit().putString("placementMode", value).apply()

    fun isModelInstalled(langCode: String): Boolean {
        return prefs.getBoolean("installed_model_$langCode", false)
    }

    fun setModelInstalled(langCode: String, installed: Boolean) {
        prefs.edit().putBoolean("installed_model_$langCode", installed).apply()
    }
}
