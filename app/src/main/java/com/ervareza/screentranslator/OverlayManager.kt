package com.ervareza.screentranslator

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val config = ConfigManager(context)
    private val handler = Handler(Looper.getMainLooper())
    private val activeViews = mutableListOf<View>()

    fun drawTranslationBubble(translatedText: String, boundingBox: Rect) {
        handler.post {
            val textView = TextView(context).apply {
                text = translatedText
                setTextColor(Color.BLACK)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }

            // Rounded Corners Background matching config opacity
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f // rounded corners for bubble
                
                // Opacity is 0-255
                val alpha = config.overlayOpacity
                setColor(Color.argb(alpha, 255, 255, 255))
                setStroke(2, Color.argb(alpha, 0, 0, 0)) // thin border
            }
            textView.background = bgDrawable

            // Calculate Position
            var xPos = boundingBox.left
            var yPos = boundingBox.top
            
            when (config.placementMode) {
                "left" -> xPos = (boundingBox.left - boundingBox.width()).coerceAtLeast(0)
                "right" -> xPos = boundingBox.right
                // "direct" uses original boundingBox.left
            }

            val params = WindowManager.LayoutParams(
                boundingBox.width().coerceAtLeast(200), // min width
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = xPos
                y = yPos
            }

            // Make it Draggable!
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            textView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(textView, params)
            activeViews.add(textView)
        }
    }

    fun clearOverlays() {
        handler.post {
            for (view in activeViews) {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    // view already removed
                }
            }
            activeViews.clear()
        }
    }
}
