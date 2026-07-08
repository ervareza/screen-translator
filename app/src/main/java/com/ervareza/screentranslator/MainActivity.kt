package com.ervareza.screentranslator

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager

    // Modern ActivityResultLauncher replaces deprecated startActivityForResult
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    private val recognizers: Map<String, TextRecognizer> by lazy {
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

    private val statusViews = mutableMapOf<String, TextView>()
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnAccessibility: MaterialButton
    private lateinit var btnNotification: MaterialButton
    private lateinit var fabStart: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        config = ConfigManager(this)
        AppCompatDelegate.setDefaultNightMode(config.appTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register ActivityResultLaunchers BEFORE lifecycle starts
        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                startForegroundService(serviceIntent)
                fabStart.text = "Service Running"
                fabStart.setIconResource(android.R.drawable.ic_media_pause)
                fabStart.isEnabled = false
                Snackbar.make(fabStart, "Service started! You can close this app.", Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(fabStart, "Screen capture permission denied.", Snackbar.LENGTH_LONG).show()
            }
        }

        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            refreshPermissionStatuses()
            if (!isGranted) {
                Snackbar.make(fabStart, "Notification permission denied. Service may run silently.", Snackbar.LENGTH_LONG).show()
            }
        }

        // Bind all views
        btnOverlay = findViewById(R.id.btnOverlayPermission)
        btnAccessibility = findViewById(R.id.btnAccessibilityPermission)
        btnNotification = findViewById(R.id.btnNotificationPermission)
        fabStart = findViewById(R.id.fabStartService)

        setupThemeToggle()
        setupDelaySlider()
        setupSourceLanguageSpinner()
        setupTargetLanguageSpinner()
        setupAIModelsManager()
        setupPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission statuses every time the user comes back from Settings
        refreshPermissionStatuses()
    }

    // ---------- Theme ----------
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

    // ---------- Delay Slider ----------
    private fun setupDelaySlider() {
        val tvDelayLabel = findViewById<TextView>(R.id.tvDelayLabel)
        val sliderDelay = findViewById<Slider>(R.id.sliderDelay)

        val currentSeconds = (config.inactivityDelayMs / 1000).toFloat()
        sliderDelay.value = currentSeconds.coerceIn(1f, 10f)
        tvDelayLabel.text = "Inactivity Delay: ${sliderDelay.value.toInt()}s"

        sliderDelay.addOnChangeListener { _, value, _ ->
            tvDelayLabel.text = "Inactivity Delay: ${value.toInt()}s"
            config.inactivityDelayMs = value.toLong() * 1000L
        }
    }

    // ---------- Source Language ----------
    private fun setupSourceLanguageSpinner() {
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerSourceLanguage)
        val sourceCodes = listOf("auto", "ja", "ko", "zh", "hi", "en")
        val sourceDisplayNames = listOf(
            "Auto-Detect (Installed Only)", "Japanese", "Korean", "Chinese", "Devanagari", "Latin/English"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sourceDisplayNames)
        spinner.setAdapter(adapter)

        val currentSourceIndex = sourceCodes.indexOf(config.sourceLanguage)
        spinner.setText(
            if (currentSourceIndex >= 0) sourceDisplayNames[currentSourceIndex] else sourceDisplayNames[0],
            false
        )

        spinner.setOnItemClickListener { _, _, position, _ ->
            config.sourceLanguage = sourceCodes[position]
        }
    }

    // ---------- Target Language ----------
    private fun setupTargetLanguageSpinner() {
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerTargetLanguage)
        val targetLangCodes = com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages()
        val targetLangNames = targetLangCodes.map { java.util.Locale(it).displayLanguage }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, targetLangNames)
        spinner.setAdapter(adapter)

        val currentIndex = targetLangCodes.indexOf(config.targetLanguage)
        spinner.setText(
            if (currentIndex >= 0) targetLangNames[currentIndex] else "Indonesian",
            false
        )

        spinner.setOnItemClickListener { _, _, position, _ ->
            config.targetLanguage = targetLangCodes[position]
        }
    }

    // ---------- AI Models Manager ----------
    private fun setupAIModelsManager() {
        val layoutModelsContainer = findViewById<LinearLayout>(R.id.layoutModelsContainer)
        val btnDownloadAll = findViewById<MaterialButton>(R.id.btnDownloadAll)

        for ((code, name) in langNames) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }

            val tvName = TextView(this).apply {
                text = name
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvStatus = TextView(this).apply {
                text = "Checking..."
                textSize = 13f
                gravity = Gravity.END
            }

            row.addView(tvName)
            row.addView(tvStatus)
            statusViews[code] = tvStatus
            layoutModelsContainer.addView(row)
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
                updateModelStatusUI(code, isInstalled)
            }.addOnFailureListener {
                config.setModelInstalled(code, false)
                statusViews[code]?.text = "Error"
            }
        }
    }

    private fun updateModelStatusUI(code: String, installed: Boolean) {
        statusViews[code]?.text = if (installed) "Installed" else "Not Installed"
        statusViews[code]?.setTextColor(
            if (installed) {
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            } else {
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            }
        )
    }

    private fun downloadAllMissingModels() {
        val client = ModuleInstall.getClient(this)
        Snackbar.make(fabStart, "Downloading missing models in background...", Snackbar.LENGTH_LONG).show()

        for ((code, recognizer) in recognizers) {
            client.areModulesAvailable(recognizer).addOnSuccessListener { response ->
                if (!response.areModulesAvailable()) {
                    statusViews[code]?.text = "Downloading..."
                    statusViews[code]?.setTextColor(
                        ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                    )

                    val listener = InstallStatusListener { update ->
                        val progress = update.progressInfo
                        if (progress != null && progress.totalBytesToDownload > 0) {
                            val pct = (progress.bytesDownloaded * 100 / progress.totalBytesToDownload).toInt()
                            runOnUiThread {
                                statusViews[code]?.text = "Downloading $pct%"
                            }
                        }
                        if (update.installState == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED) {
                            runOnUiThread {
                                config.setModelInstalled(code, true)
                                updateModelStatusUI(code, true)
                            }
                        }
                    }

                    val request = ModuleInstallRequest.newBuilder()
                        .addApi(recognizer)
                        .setListener(listener)
                        .build()

                    client.installModules(request).addOnSuccessListener { installResponse ->
                        if (installResponse.areModulesAlreadyInstalled()) {
                            config.setModelInstalled(code, true)
                            updateModelStatusUI(code, true)
                        }
                    }.addOnFailureListener {
                        config.setModelInstalled(code, false)
                        statusViews[code]?.text = "Failed"
                        statusViews[code]?.setTextColor(
                            ContextCompat.getColor(this, android.R.color.holo_red_dark)
                        )
                    }
                }
            }
        }
    }

    // ---------- Permissions & Start ----------
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            val info = service.resolveInfo.serviceInfo
            if (info.packageName == packageName && info.name == InactivityAccessibilityService::class.java.name) {
                return true
            }
        }
        return false
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-13, no runtime permission needed
        }
    }

    private fun refreshPermissionStatuses() {
        // Overlay
        val overlayGranted = Settings.canDrawOverlays(this)
        btnOverlay.text = if (overlayGranted) "Overlay Permission  --  Granted" else "Grant Overlay Permission"
        btnOverlay.setIconResource(
            if (overlayGranted) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background
        )
        btnOverlay.isEnabled = !overlayGranted

        // Accessibility
        val accessibilityGranted = isAccessibilityServiceEnabled()
        btnAccessibility.text = if (accessibilityGranted) "Accessibility Service  --  Enabled" else "Enable Accessibility Service"
        btnAccessibility.setIconResource(
            if (accessibilityGranted) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background
        )
        btnAccessibility.isEnabled = !accessibilityGranted

        // Notification
        val notifGranted = isNotificationPermissionGranted()
        btnNotification.text = if (notifGranted) "Notification Permission  --  Granted" else "Grant Notification Permission"
        btnNotification.setIconResource(
            if (notifGranted) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background
        )
        btnNotification.isEnabled = !notifGranted

        // Enable FAB only if essential permissions are granted
        val allEssentialGranted = overlayGranted && accessibilityGranted
        fabStart.isEnabled = allEssentialGranted
        if (!allEssentialGranted) {
            fabStart.text = "Grant Permissions First"
            fabStart.setIconResource(android.R.drawable.ic_dialog_alert)
        } else {
            fabStart.text = "Start Service"
            fabStart.setIconResource(android.R.drawable.ic_media_play)
        }
    }

    private fun setupPermissionsAndStart() {
        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        fabStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Snackbar.make(fabStart, "Please grant overlay permission first.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isAccessibilityServiceEnabled()) {
                Snackbar.make(fabStart, "Please enable accessibility service first.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startScreenCapture()
        }

        refreshPermissionStatuses()
    }

    private fun startScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}
