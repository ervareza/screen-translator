package com.ervareza.screentranslator

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class InactivityAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var config: ConfigManager
    
    private val triggerTranslationRunnable = Runnable {
        Log.d("Translator", "Inactivity detected. Triggering translation...")
        // Explicit broadcast with package name so it reaches RECEIVER_NOT_EXPORTED
        val intent = Intent("com.ervareza.screentranslator.TRIGGER_CAPTURE")
        intent.setPackage("com.ervareza.screentranslator")
        sendBroadcast(intent)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        config = ConfigManager(this)
        Log.d("Translator", "Accessibility Service Connected")
        resetTimer()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Any scroll or window change means user is interacting
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED || 
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            resetTimer()
        }
    }

    private fun resetTimer() {
        handler.removeCallbacks(triggerTranslationRunnable)
        // Set new timer based on user config
        handler.postDelayed(triggerTranslationRunnable, config.inactivityDelayMs)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(triggerTranslationRunnable)
    }
}
