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
import java.util.concurrent.CopyOnWriteArrayList

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val config = ConfigManager(context)
    private val handler = Handler(Looper.getMainLooper())

    // ISSUE-005 FIX: Thread-safe list to prevent ConcurrentModificationException
    private val activeViews = CopyOnWriteArrayList<View>()

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun drawTranslationBubble(translatedText: String, boundingBox: Rect) {
        handler.post {
            val textView = TextView(context).apply {
                text = translatedText
                setTextColor(Color.parseColor(config.bubbleTextColor))
                textSize = config.overlayTextSize.toFloat()
                gravity = Gravity.CENTER
                val pad = dpToPx(8)
                setPadding(pad, pad, pad, pad)
            }

            val alpha = config.overlayOpacity
            val bgColor = Color.parseColor(config.bubbleBgColor)
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(config.bubbleCornerRadius).toFloat()

                setColor(Color.argb(
                    alpha,
                    Color.red(bgColor),
                    Color.green(bgColor),
                    Color.blue(bgColor)
                ))

                if (config.bubbleBorderEnabled) {
                    val borderColor = Color.parseColor(config.bubbleTextColor)
                    setStroke(2, Color.argb(alpha, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor)))
                }
            }
            textView.background = bgDrawable

            var xPos = boundingBox.left
            var yPos = boundingBox.top

            when (config.placementMode) {
                "left" -> xPos = (boundingBox.left - boundingBox.width()).coerceAtLeast(0)
                "right" -> xPos = boundingBox.right
            }

            // ISSUE-007 FIX: Use dp-based minimum width instead of raw pixels
            val minWidthPx = dpToPx(100)
            val params = WindowManager.LayoutParams(
                boundingBox.width().coerceAtLeast(minWidthPx),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = xPos
                y = yPos
            }

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

            val autoClear = config.autoClearSeconds
            if (autoClear > 0) {
                handler.postDelayed({
                    try {
                        windowManager.removeView(textView)
                        activeViews.remove(textView)
                    } catch (_: Exception) {}
                }, autoClear * 1000L)
            }
        }
    }

    fun clearOverlays() {
        handler.post {
            for (view in activeViews) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {}
            }
            activeViews.clear()
        }
    }
}
