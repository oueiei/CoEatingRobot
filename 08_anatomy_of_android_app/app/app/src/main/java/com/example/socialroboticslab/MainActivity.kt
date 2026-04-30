package com.example.socialroboticslab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

        findViewById<Button>(R.id.btnHttpChat).setOnClickListener {
            startActivity(UnitEntryActivity.createIntent(this, UnitEntryActivity.UNIT_HTTP_CHAT))
        }

        findViewById<Button>(R.id.btnWsAudio).setOnClickListener {
            startActivity(UnitEntryActivity.createIntent(this, UnitEntryActivity.UNIT_WS_AUDIO))
        }

        findViewById<Button>(R.id.btnWebrtcStream).setOnClickListener {
            startActivity(UnitEntryActivity.createIntent(this, UnitEntryActivity.UNIT_VIDEO_STREAM))
        }

        findViewById<Button>(R.id.btnVideoCall).setOnClickListener {
            startActivity(UnitEntryActivity.createIntent(this, UnitEntryActivity.UNIT_VIDEO_CALL))
        }

        findViewById<Button>(R.id.btnWozController).setOnClickListener {
            startActivity(UnitEntryActivity.createIntent(this, UnitEntryActivity.UNIT_WOZ))
        }

        findViewById<Button>(R.id.btnCoEatingAgent).setOnClickListener {
            startActivity(UnitEntryActivity.createIntent(this, UnitEntryActivity.UNIT_HTTP_CHAT_CEA))
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECORD_AUDIO)
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        }
    }
}
