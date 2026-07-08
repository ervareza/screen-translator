package com.ervareza.screentranslator

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class InactivityAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    // Use nullable instead of lateinit to prevent crash when onAccessibilityEvent
    // fires before onServiceConnected
    private var config: ConfigManager? = null
    private var serviceReady = false

    private val triggerTranslationRunnable = Runnable {
        Log.d("Translator", "Inactivity detected. Triggering translation...")
        try {
            // Explicit broadcast with package name so it reaches RECEIVER_NOT_EXPORTED
            val intent = Intent("com.ervareza.screentranslator.TRIGGER_CAPTURE")
            intent.setPackage("com.ervareza.screentranslator")
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("Translator", "Failed to send broadcast", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            config = ConfigManager(this)
            serviceReady = true
            Log.d("Translator", "Accessibility Service Connected")
            resetTimer()
        } catch (e: Exception) {
            Log.e("Translator", "Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Guard: do nothing if service is not fully initialized yet
        if (!serviceReady || config == null) return

        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            resetTimer()
        }
    }

    private fun resetTimer() {
        handler.removeCallbacks(triggerTranslationRunnable)
        val delay = config?.inactivityDelayMs ?: 3000L
        handler.postDelayed(triggerTranslationRunnable, delay)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(triggerTranslationRunnable)
        serviceReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(triggerTranslationRunnable)
        serviceReady = false
    }
}
