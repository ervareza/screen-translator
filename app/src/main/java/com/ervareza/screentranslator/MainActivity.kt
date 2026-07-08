package com.ervareza.screentranslator

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

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
        config = ConfigManager(this)
        AppCompatDelegate.setDefaultNightMode(config.appTheme)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupThemeToggle()
        setupDelaySlider()
        setupSourceLanguageSpinner()
        setupTargetLanguageSpinner()
        setupAIModelsManager()
        setupPermissionsAndStart()
    }

    private fun setupThemeToggle() {
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.themeToggleGroup)
        when (config.appTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> toggleGroup.check(R.id.btnThemeLight)
            AppCompatDelegate.MODE_NIGHT_YES -> toggleGroup.check(R.id.btnThemeDark)
            else -> toggleGroup.check(R.id.btnThemeSystem)
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.btnThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btnThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (config.appTheme != mode) {
                    config.appTheme = mode
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
        }
    }

    private fun setupDelaySlider() {
        val tvDelayLabel = findViewById<TextView>(R.id.tvDelayLabel)
        val sliderDelay = findViewById<Slider>(R.id.sliderDelay)
        
        val currentSeconds = (config.inactivityDelayMs / 1000).toFloat()
        sliderDelay.value = if (currentSeconds < 1f) 1f else currentSeconds
        tvDelayLabel.text = "Inactivity Delay: ${sliderDelay.value.toInt()}s"

        sliderDelay.addOnChangeListener { _, value, _ ->
            tvDelayLabel.text = "Inactivity Delay: ${value.toInt()}s"
            config.inactivityDelayMs = value.toLong() * 1000L
        }
    }

    private fun setupSourceLanguageSpinner() {
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerSourceLanguage)
        val sourceCodes = listOf("auto", "ja", "ko", "zh", "hi", "en")
        val sourceDisplayNames = listOf("Auto-Detect (Installed Only)", "Japanese", "Korean", "Chinese", "Devanagari", "Latin/English")

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sourceDisplayNames)
        spinner.setAdapter(adapter)

        val currentSourceIndex = sourceCodes.indexOf(config.sourceLanguage)
        if (currentSourceIndex >= 0) {
            spinner.setText(sourceDisplayNames[currentSourceIndex], false)
        } else {
            spinner.setText(sourceDisplayNames[0], false)
        }

        spinner.setOnItemClickListener { _, _, position, _ ->
            config.sourceLanguage = sourceCodes[position]
        }
    }

    private fun setupTargetLanguageSpinner() {
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerTargetLanguage)
        val targetLangCodes = com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages()
        val targetLangNames = targetLangCodes.map { java.util.Locale(it).displayLanguage }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, targetLangNames)
        spinner.setAdapter(adapter)

        val currentIndex = targetLangCodes.indexOf(config.targetLanguage)
        if (currentIndex >= 0) {
            spinner.setText(targetLangNames[currentIndex], false)
        } else {
            spinner.setText("Indonesian", false)
        }

        spinner.setOnItemClickListener { _, _, position, _ ->
            config.targetLanguage = targetLangCodes[position]
        }
    }

    private val statusViews = mutableMapOf<String, TextView>()

    private fun setupAIModelsManager() {
        val layoutModelsContainer = findViewById<LinearLayout>(R.id.layoutModelsContainer)
        val btnDownloadAll = findViewById<MaterialButton>(R.id.btnDownloadAll)

        for ((code, name) in langNames) {
            val tv = TextView(this).apply {
                text = "$name: Checking..."
                setPadding(0, 8, 0, 8)
            }
            statusViews[code] = tv
            layoutModelsContainer.addView(tv)
        }

        btnDownloadAll.setOnClickListener {
            downloadAllMissingModels()
        }

        checkModelStatuses()
    }

    private fun checkModelStatuses() {
        val client = ModuleInstall.getClient(this)
        for ((code, recognizer) in recognizers) {
            client.areModulesAvailable(recognizer).addOnSuccessListener { response ->
                val isInstalled = response.areModulesAvailable()
                config.setModelInstalled(code, isInstalled)
                val status = if (isInstalled) "Installed" else "Not Installed"
                statusViews[code]?.text = "${langNames[code]}: $status"
            }.addOnFailureListener {
                config.setModelInstalled(code, false)
                statusViews[code]?.text = "${langNames[code]}: Error checking"
            }
        }
    }

    private fun downloadAllMissingModels() {
        val client = ModuleInstall.getClient(this)
        Toast.makeText(this, "Checking & downloading models in background...", Toast.LENGTH_SHORT).show()
        
        for ((code, recognizer) in recognizers) {
            client.areModulesAvailable(recognizer).addOnSuccessListener { response ->
                if (!response.areModulesAvailable()) {
                    statusViews[code]?.text = "${langNames[code]}: Downloading via Play Services..."
                    val request = ModuleInstallRequest.newBuilder().addApi(recognizer).build()
                    client.installModules(request).addOnSuccessListener {
                        config.setModelInstalled(code, true)
                        statusViews[code]?.text = "${langNames[code]}: Installed"
                    }.addOnFailureListener {
                        config.setModelInstalled(code, false)
                        statusViews[code]?.text = "${langNames[code]}: Download Failed"
                    }
                }
            }
        }
    }

    private fun setupPermissionsAndStart() {
        findViewById<MaterialButton>(R.id.btnOverlayPermission).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay already granted", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnAccessibilityPermission).setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabStartService).setOnClickListener {
            startScreenCapture()
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
