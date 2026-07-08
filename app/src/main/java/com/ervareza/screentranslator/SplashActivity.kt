package com.ervareza.screentranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val videoView = findViewById<VideoView>(R.id.splashVideo)
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.splash_video}")
        videoView.setVideoURI(videoUri)

        videoView.setOnCompletionListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        videoView.setOnErrorListener { _, _, _ ->
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            true
        }

        videoView.start()
    }
}
