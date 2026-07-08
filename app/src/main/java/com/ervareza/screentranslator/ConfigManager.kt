package com.ervareza.screentranslator

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ScreenTranslatorPrefs", Context.MODE_PRIVATE)

    // ---------- General ----------
    var inactivityDelayMs: Long
        get() = prefs.getLong("inactivityDelayMs", 3000L)
        set(value) = prefs.edit().putLong("inactivityDelayMs", value).apply()

    var appTheme: Int
        get() = prefs.getInt("appTheme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt("appTheme", value).apply()

    // ---------- Language ----------
    var targetLanguage: String
        get() = prefs.getString("targetLanguage", "id") ?: "id"
        set(value) = prefs.edit().putString("targetLanguage", value).apply()

    var sourceLanguage: String
        get() = prefs.getString("sourceLanguage", "auto") ?: "auto"
        set(value) = prefs.edit().putString("sourceLanguage", value).apply()

    // ---------- Overlay Customization ----------

    // "direct" = over original text, "left" = bubble to the left, "right" = bubble to the right
    var placementMode: String
        get() = prefs.getString("placementMode", "direct") ?: "direct"
        set(value) = prefs.edit().putString("placementMode", value).apply()

    // 0-255 alpha for bubble background
    var overlayOpacity: Int
        get() = prefs.getInt("overlayOpacity", 230)
        set(value) = prefs.edit().putInt("overlayOpacity", value).apply()

    // Bubble corner radius in dp
    var bubbleCornerRadius: Int
        get() = prefs.getInt("bubbleCornerRadius", 16)
        set(value) = prefs.edit().putInt("bubbleCornerRadius", value).apply()

    // Translated text size in sp
    var overlayTextSize: Int
        get() = prefs.getInt("overlayTextSize", 14)
        set(value) = prefs.edit().putInt("overlayTextSize", value).apply()

    // Bubble background color as ARGB hex string (without alpha)
    var bubbleBgColor: String
        get() = prefs.getString("bubbleBgColor", "#FFFFFF") ?: "#FFFFFF"
        set(value) = prefs.edit().putString("bubbleBgColor", value).apply()

    // Bubble text color as ARGB hex string
    var bubbleTextColor: String
        get() = prefs.getString("bubbleTextColor", "#000000") ?: "#000000"
        set(value) = prefs.edit().putString("bubbleTextColor", value).apply()

    // Show border on bubble
    var bubbleBorderEnabled: Boolean
        get() = prefs.getBoolean("bubbleBorderEnabled", true)
        set(value) = prefs.edit().putBoolean("bubbleBorderEnabled", value).apply()

    // Auto-clear translation overlays after X seconds (0 = manual only)
    var autoClearSeconds: Int
        get() = prefs.getInt("autoClearSeconds", 0)
        set(value) = prefs.edit().putInt("autoClearSeconds", value).apply()

    // ---------- Model Tracking ----------
    fun isModelInstalled(langCode: String): Boolean {
        return prefs.getBoolean("installed_model_$langCode", false)
    }

    fun setModelInstalled(langCode: String, installed: Boolean) {
        prefs.edit().putBoolean("installed_model_$langCode", installed).apply()
    }
}
