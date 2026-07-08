package com.ervareza.screentranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val videoView = findViewById<VideoView>(R.id.splashVideo)
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.splash_video}")
        videoView.setVideoURI(videoUri)

        videoView.setOnCompletionListener { goToMain() }
        videoView.setOnErrorListener { _, _, _ -> goToMain(); true }

        // ISSUE-009 FIX: Tap anywhere to skip splash
        videoView.setOnClickListener { goToMain() }

        videoView.start()
    }

    private fun goToMain() {
        if (!navigated) {
            navigated = true
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
