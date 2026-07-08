package com.ervareza.screentranslator

import android.Manifest
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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

class MainActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager
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

        // Register launchers before lifecycle starts
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
        ) { _ ->
            refreshPermissionStatuses()
        }

        btnOverlay = findViewById(R.id.btnOverlayPermission)
        btnAccessibility = findViewById(R.id.btnAccessibilityPermission)
        btnNotification = findViewById(R.id.btnNotificationPermission)
        fabStart = findViewById(R.id.fabStartService)

        setupThemeToggle()
        setupDelaySlider()
        setupSourceLanguageSpinner()
        setupTargetLanguageSpinner()
        setupOverlayCustomization()
        setupAIModelsManager()
        setupPermissionsAndStart()
    }

    private val serviceStopReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.ervareza.screentranslator.SERVICE_STOPPED") {
                refreshPermissionStatuses()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatuses()

        val filter = android.content.IntentFilter("com.ervareza.screentranslator.SERVICE_STOPPED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStopReceiver, filter)
        }

        // ISSUE-006 FIX: Sync FAB with actual service state
        if (isServiceRunning(ScreenCaptureService::class.java)) {
            fabStart.text = "Service Running"
            fabStart.setIconResource(android.R.drawable.ic_media_pause)
            fabStart.isEnabled = true
            fabStart.setOnClickListener {
                val stopBroadcast = Intent("com.ervareza.screentranslator.SERVICE_STOPPED")
                stopBroadcast.setPackage(packageName)
                sendBroadcast(stopBroadcast)
                
                val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = "ACTION_STOP"
                }
                startService(stopIntent)
            }
        } else {
            fabStart.text = "Start Service"
            fabStart.setIconResource(android.R.drawable.ic_media_play)
            fabStart.isEnabled = true
            fabStart.setOnClickListener { setupPermissionsAndStart() }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(serviceStopReceiver)
        } catch (e: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    // ==================== THEME ====================
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

    // ==================== DELAY ====================
    private fun setupDelaySlider() {
        val tvDelayLabel = findViewById<TextView>(R.id.tvDelayLabel)
        val sliderDelay = findViewById<Slider>(R.id.sliderDelay)
        val currentSeconds = (config.inactivityDelayMs / 1000).toFloat().coerceIn(1f, 10f)
        sliderDelay.value = currentSeconds
        tvDelayLabel.text = "Inactivity Delay: ${currentSeconds.toInt()}s"
        sliderDelay.addOnChangeListener { _, value, _ ->
            tvDelayLabel.text = "Inactivity Delay: ${value.toInt()}s"
            config.inactivityDelayMs = value.toLong() * 1000L
        }
    }

    // ==================== LANGUAGE ====================
    private fun setupSourceLanguageSpinner() {
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerSourceLanguage)
        val sourceCodes = listOf("auto", "ja", "ko", "zh", "hi", "en")
        val sourceDisplayNames = listOf("Auto-Detect (Installed Only)", "Japanese", "Korean", "Chinese", "Devanagari", "Latin/English")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sourceDisplayNames)
        spinner.setAdapter(adapter)
        val idx = sourceCodes.indexOf(config.sourceLanguage)
        spinner.setText(if (idx >= 0) sourceDisplayNames[idx] else sourceDisplayNames[0], false)
        spinner.setOnItemClickListener { _, _, position, _ -> config.sourceLanguage = sourceCodes[position] }
    }

    private fun setupTargetLanguageSpinner() {
        val spinner = findViewById<AutoCompleteTextView>(R.id.spinnerTargetLanguage)
        val codes = com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages()
        val names = codes.map { java.util.Locale(it).displayLanguage }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        spinner.setAdapter(adapter)
        val idx = codes.indexOf(config.targetLanguage)
        spinner.setText(if (idx >= 0) names[idx] else "Indonesian", false)
        spinner.setOnItemClickListener { _, _, position, _ -> config.targetLanguage = codes[position] }
    }

    // ==================== OVERLAY CUSTOMIZATION ====================
    private fun setupOverlayCustomization() {
        // Placement
        val placementGroup = findViewById<MaterialButtonToggleGroup>(R.id.placementToggleGroup)
        when (config.placementMode) {
            "left" -> placementGroup.check(R.id.btnPlaceLeft)
            "right" -> placementGroup.check(R.id.btnPlaceRight)
            else -> placementGroup.check(R.id.btnPlaceDirect)
        }
        placementGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                config.placementMode = when (checkedId) {
                    R.id.btnPlaceLeft -> "left"
                    R.id.btnPlaceRight -> "right"
                    else -> "direct"
                }
            }
        }

        // Opacity
        val tvOpacity = findViewById<TextView>(R.id.tvOpacityLabel)
        val sliderOpacity = findViewById<Slider>(R.id.sliderOpacity)
        val opacityPct = (config.overlayOpacity * 100 / 255).toFloat().coerceIn(10f, 100f)
        sliderOpacity.value = opacityPct
        tvOpacity.text = "Bubble Opacity: ${opacityPct.toInt()}%"
        sliderOpacity.addOnChangeListener { _, value, _ ->
            tvOpacity.text = "Bubble Opacity: ${value.toInt()}%"
            config.overlayOpacity = (value * 255 / 100).toInt()
        }

        // Corner Radius
        val tvCorner = findViewById<TextView>(R.id.tvCornerLabel)
        val sliderCorner = findViewById<Slider>(R.id.sliderCorner)
        sliderCorner.value = config.bubbleCornerRadius.toFloat().coerceIn(0f, 32f)
        tvCorner.text = "Corner Radius: ${config.bubbleCornerRadius}dp"
        sliderCorner.addOnChangeListener { _, value, _ ->
            tvCorner.text = "Corner Radius: ${value.toInt()}dp"
            config.bubbleCornerRadius = value.toInt()
        }

        // Text Size
        val tvTextSize = findViewById<TextView>(R.id.tvTextSizeLabel)
        val sliderTextSize = findViewById<Slider>(R.id.sliderTextSize)
        sliderTextSize.value = config.overlayTextSize.toFloat().coerceIn(8f, 28f)
        tvTextSize.text = "Text Size: ${config.overlayTextSize}sp"
        sliderTextSize.addOnChangeListener { _, value, _ ->
            tvTextSize.text = "Text Size: ${value.toInt()}sp"
            config.overlayTextSize = value.toInt()
        }

        // Bubble Color
        val bgColorGroup = findViewById<MaterialButtonToggleGroup>(R.id.bgColorToggleGroup)
        when (config.bubbleBgColor) {
            "#000000" -> {
                bgColorGroup.check(R.id.btnBgBlack)
                config.bubbleTextColor = "#FFFFFF"
            }
            "#FFFDE7" -> bgColorGroup.check(R.id.btnBgYellow)
            else -> bgColorGroup.check(R.id.btnBgWhite)
        }
        bgColorGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnBgWhite -> {
                        config.bubbleBgColor = "#FFFFFF"
                        config.bubbleTextColor = "#000000"
                    }
                    R.id.btnBgBlack -> {
                        config.bubbleBgColor = "#000000"
                        config.bubbleTextColor = "#FFFFFF"
                    }
                    R.id.btnBgYellow -> {
                        config.bubbleBgColor = "#FFFDE7"
                        config.bubbleTextColor = "#000000"
                    }
                }
            }
        }

        // Border Toggle
        val switchBorder = findViewById<MaterialSwitch>(R.id.switchBorder)
        switchBorder.isChecked = config.bubbleBorderEnabled
        switchBorder.setOnCheckedChangeListener { _, isChecked ->
            config.bubbleBorderEnabled = isChecked
        }

        // Auto-Clear
        val tvAutoClear = findViewById<TextView>(R.id.tvAutoClearLabel)
        val sliderAutoClear = findViewById<Slider>(R.id.sliderAutoClear)
        sliderAutoClear.value = config.autoClearSeconds.toFloat().coerceIn(0f, 30f)
        tvAutoClear.text = if (config.autoClearSeconds == 0) "Auto-Clear: Off" else "Auto-Clear: ${config.autoClearSeconds}s"
        sliderAutoClear.addOnChangeListener { _, value, _ ->
            val sec = value.toInt()
            tvAutoClear.text = if (sec == 0) "Auto-Clear: Off" else "Auto-Clear: ${sec}s"
            config.autoClearSeconds = sec
        }
    }

    // ==================== AI MODELS ====================
    private fun setupAIModelsManager() {
        val container = findViewById<LinearLayout>(R.id.layoutModelsContainer)
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
            container.addView(row)
        }

        btnDownloadAll.setOnClickListener { downloadAllMissingModels() }
        checkModelStatuses()
    }

    private val translateModelCodes = mapOf(
        "ja" to TranslateLanguage.JAPANESE,
        "ko" to TranslateLanguage.KOREAN,
        "zh" to TranslateLanguage.CHINESE,
        "hi" to TranslateLanguage.HINDI,
        "en" to TranslateLanguage.ENGLISH
    )

    private fun checkModelStatuses() {
        val modelManager = RemoteModelManager.getInstance()
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java).addOnSuccessListener { models ->
            val downloadedTags = models.map { it.language }
            
            for ((code, langTag) in translateModelCodes) {
                val isInstalled = downloadedTags.contains(langTag)
                config.setModelInstalled(code, isInstalled)
                updateModelStatusUI(code, isInstalled)
            }
        }.addOnFailureListener {
            for (code in translateModelCodes.keys) {
                config.setModelInstalled(code, false)
                statusViews[code]?.text = "Error"
            }
        }
    }

    private fun updateModelStatusUI(code: String, installed: Boolean) {
        statusViews[code]?.text = if (installed) "Installed" else "Not Installed"
        statusViews[code]?.setTextColor(
            if (installed) ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else ContextCompat.getColor(this, android.R.color.holo_red_light)
        )
    }

    private fun downloadAllMissingModels() {
        val modelManager = RemoteModelManager.getInstance()
        val conditions = DownloadConditions.Builder().build()
        
        Snackbar.make(fabStart, "Downloading missing translation models...", Snackbar.LENGTH_LONG).show()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ModelDownloadChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Model Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        for ((code, langTag) in translateModelCodes) {
            if (!config.isModelInstalled(code)) {
                statusViews[code]?.text = "Downloading..."
                statusViews[code]?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                
                val notificationId = 200 + code.hashCode()
                val notificationBuilder = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Downloading ${langNames[code]} Translation Model")
                    .setProgress(0, 0, true)
                    .setOngoing(true)
                
                notificationManager.notify(notificationId, notificationBuilder.build())

                val model = TranslateRemoteModel.Builder(langTag).build()
                modelManager.download(model, conditions)
                    .addOnSuccessListener {
                        config.setModelInstalled(code, true)
                        updateModelStatusUI(code, true)
                        
                        notificationBuilder.setContentText("Download complete")
                            .setProgress(0, 0, false)
                            .setOngoing(false)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        notificationManager.notify(notificationId, notificationBuilder.build())
                    }
                    .addOnFailureListener {
                        statusViews[code]?.text = "Failed"
                        statusViews[code]?.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        
                        notificationBuilder.setContentText("Download failed")
                            .setProgress(0, 0, false)
                            .setOngoing(false)
                            .setSmallIcon(android.R.drawable.stat_notify_error)
                        notificationManager.notify(notificationId, notificationBuilder.build())
                    }
            }
        }
    }

    // ==================== PERMISSIONS ====================
    private fun isAccessibilityServiceEnabled(): Boolean {
        // Use Settings.Secure for reliable detection
        val expectedId = "$packageName/${InactivityAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedId, ignoreCase = true) }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun refreshPermissionStatuses() {
        val overlayOk = Settings.canDrawOverlays(this)
        btnOverlay.text = if (overlayOk) "Overlay Permission  --  Granted" else "Grant Overlay Permission"
        btnOverlay.setIconResource(if (overlayOk) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background)
        btnOverlay.isEnabled = !overlayOk

        val accessOk = isAccessibilityServiceEnabled()
        btnAccessibility.text = if (accessOk) "Accessibility  --  Enabled" else "Enable Accessibility Service"
        btnAccessibility.setIconResource(if (accessOk) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background)
        btnAccessibility.isEnabled = !accessOk

        val notifOk = isNotificationPermissionGranted()
        btnNotification.text = if (notifOk) "Notification  --  Granted" else "Grant Notification Permission"
        btnNotification.setIconResource(if (notifOk) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background)
        btnNotification.isEnabled = !notifOk

        val allReady = overlayOk && accessOk
        fabStart.isEnabled = allReady
        if (!allReady) {
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
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
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
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
    }
}
