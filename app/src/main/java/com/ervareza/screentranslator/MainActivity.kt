package com.ervareza.screentranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : Activity() {

    private lateinit var config: ConfigManager
    private val SCREEN_CAPTURE_REQUEST_CODE = 1001

    private val recognizers by lazy {
        mapOf(
            "ja" to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            "ko" to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
            "zh" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            "hi" to TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()),
            "en" to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        )
    }

    private val langNames = mapOf(
        "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "hi" to "Devanagari", "en" to "Latin/English"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigManager(this)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        scrollView.addView(layout)

        // Title
        layout.addView(TextView(this).apply {
            text = "Screen Translator Settings"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        })

        // Delay Slider
        val delayLabel = TextView(this).apply { text = "Inactivity Delay: ${config.inactivityDelayMs / 1000}s" }
        val delaySlider = SeekBar(this).apply {
            max = 10
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

        // Source Language Spinner (OCR Model)
        val sourceLangLabel = TextView(this).apply { 
            text = "Source Language (Comic Language):" 
            setPadding(0, 32, 0, 8)
        }
        layout.addView(sourceLangLabel)

        val sourceSpinner = android.widget.Spinner(this)
        val sourceCodes = listOf("auto", "ja", "ko", "zh", "hi", "en")
        val sourceDisplayNames = listOf("🤖 Auto-Detect (Installed Only)", "Japanese", "Korean", "Chinese", "Devanagari", "Latin/English")

        val sourceAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, sourceDisplayNames)
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sourceSpinner.adapter = sourceAdapter

        val currentSourceIndex = sourceCodes.indexOf(config.sourceLanguage)
        if (currentSourceIndex >= 0) sourceSpinner.setSelection(currentSourceIndex)

        sourceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                config.sourceLanguage = sourceCodes[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        layout.addView(sourceSpinner)

        // Target Language Spinner
        val targetLangLabel = TextView(this).apply { 
            text = "Target Language:" 
            setPadding(0, 32, 0, 8)
        }
        layout.addView(targetLangLabel)

        val spinner = android.widget.Spinner(this)
        val targetLangCodes = com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages()
        val targetLangNames = targetLangCodes.map { java.util.Locale(it).displayLanguage }

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, targetLangNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val currentIndex = targetLangCodes.indexOf(config.targetLanguage)
        if (currentIndex >= 0) spinner.setSelection(currentIndex)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                config.targetLanguage = targetLangCodes[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        layout.addView(spinner)
        
        // AI Models Manager Section
        layout.addView(TextView(this).apply {
            text = "AI Models Manager (OCR)"
            textSize = 20f
            setPadding(0, 48, 0, 16)
        })

        val statusViews = mutableMapOf<String, TextView>()
        for ((code, name) in langNames) {
            val tv = TextView(this).apply {
                text = "$name: 🔄 Checking..."
                setPadding(0, 8, 0, 8)
            }
            statusViews[code] = tv
            layout.addView(tv)
        }

        val btnDownloadAll = Button(this).apply {
            text = "Download All Missing Models"
            setOnClickListener {
                downloadAllMissingModels(statusViews)
            }
        }
        layout.addView(btnDownloadAll)

        checkModelStatuses(statusViews)

        // Spacer
        layout.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 48) })

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

        setContentView(scrollView)
    }

    private fun checkModelStatuses(statusViews: Map<String, TextView>) {
        val client = ModuleInstall.getClient(this)
        for ((code, recognizer) in recognizers) {
            client.areModulesAvailable(recognizer).addOnSuccessListener { response ->
                val isInstalled = response.areModulesAvailable()
                config.setModelInstalled(code, isInstalled)
                val status = if (isInstalled) "🟢 Installed" else "🔴 Not Installed"
                statusViews[code]?.text = "${langNames[code]}: $status"
            }.addOnFailureListener {
                config.setModelInstalled(code, false)
                statusViews[code]?.text = "${langNames[code]}: ❓ Error checking"
            }
        }
    }

    private fun downloadAllMissingModels(statusViews: Map<String, TextView>) {
        val client = ModuleInstall.getClient(this)
        Toast.makeText(this, "Checking & downloading models in background...", Toast.LENGTH_SHORT).show()
        
        for ((code, recognizer) in recognizers) {
            client.areModulesAvailable(recognizer).addOnSuccessListener { response ->
                if (!response.areModulesAvailable()) {
                    statusViews[code]?.text = "${langNames[code]}: ⏳ Downloading via Play Services..."
                    val request = ModuleInstallRequest.newBuilder().addApi(recognizer).build()
                    client.installModules(request).addOnSuccessListener {
                        config.setModelInstalled(code, true)
                        statusViews[code]?.text = "${langNames[code]}: 🟢 Installed"
                    }.addOnFailureListener {
                        config.setModelInstalled(code, false)
                        statusViews[code]?.text = "${langNames[code]}: ❌ Download Failed"
                    }
                }
            }
        }
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
