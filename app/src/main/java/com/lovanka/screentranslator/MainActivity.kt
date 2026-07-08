package com.lovanka.screentranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var config: ConfigManager
    private val SCREEN_CAPTURE_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigManager(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "Screen Translator Settings"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        })

        // Delay Slider
        val delayLabel = TextView(this).apply { text = "Inactivity Delay: ${config.inactivityDelayMs / 1000}s" }
        val delaySlider = SeekBar(this).apply {
            max = 10 // max 10 seconds
            progress = (config.inactivityDelayMs / 1000).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val p = if (progress == 0) 1 else progress
                    delayLabel.text = "Inactivity Delay: ${p}s"
                    config.inactivityDelayMs = p * 1000L
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(delayLabel)
        layout.addView(delaySlider)

        // Target Language Spinner
        val targetLangLabel = TextView(this).apply { 
            text = "Target Language:" 
            setPadding(0, 32, 0, 8)
        }
        layout.addView(targetLangLabel)

        val spinner = android.widget.Spinner(this)
        val langCodes = com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages()
        val langNames = langCodes.map { java.util.Locale(it).displayLanguage }

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, langNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Set current selection based on config
        val currentIndex = langCodes.indexOf(config.targetLanguage)
        if (currentIndex >= 0) spinner.setSelection(currentIndex)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                config.targetLanguage = langCodes[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        layout.addView(spinner)
        
        // Spacer
        layout.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 32) })

        // Permission: Overlay
        val btnOverlay = Button(this).apply {
            text = "Grant Overlay Permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Overlay already granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(btnOverlay)

        // Permission: Accessibility
        val btnAccessibility = Button(this).apply {
            text = "Enable Accessibility Service"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        }
        layout.addView(btnAccessibility)

        // Start Capture Service
        val btnStart = Button(this).apply {
            text = "Start Screen Translation Service"
            setOnClickListener {
                startScreenCapture()
            }
        }
        layout.addView(btnStart)

        setContentView(layout)
    }

    private fun startScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }
                startForegroundService(intent)
                Toast.makeText(this, "Service Started!", Toast.LENGTH_SHORT).show()
                finish() // Close the app UI
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
