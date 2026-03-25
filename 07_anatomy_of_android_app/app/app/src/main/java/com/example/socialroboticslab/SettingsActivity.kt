package com.example.socialroboticslab

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.socialroboticslab.util.PrefsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var etHttpUrl: EditText
    private lateinit var etWsAudioUrl: EditText
    private lateinit var etWebrtcStreamUrl: EditText
    private lateinit var etVideoCallUrl: EditText
    private lateinit var etStunUrl: EditText
    private lateinit var etTurnUrl: EditText
    private lateinit var etTurnUser: EditText
    private lateinit var etTurnPass: EditText
    private lateinit var etWozUrl: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.title = "Server Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etHttpUrl = findViewById(R.id.etHttpUrl)
        etWsAudioUrl = findViewById(R.id.etWsAudioUrl)
        etWebrtcStreamUrl = findViewById(R.id.etWebrtcStreamUrl)
        etVideoCallUrl = findViewById(R.id.etVideoCallUrl)
        etStunUrl = findViewById(R.id.etStunUrl)
        etTurnUrl = findViewById(R.id.etTurnUrl)
        etTurnUser = findViewById(R.id.etTurnUser)
        etTurnPass = findViewById(R.id.etTurnPass)
        etWozUrl = findViewById(R.id.etWozUrl)

        loadSettings()

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveSettings() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetDefaults() }
    }

    private fun loadSettings() {
        etHttpUrl.setText(PrefsManager.getHttpUrl(this))
        etWsAudioUrl.setText(PrefsManager.getWsAudioUrl(this))
        etWebrtcStreamUrl.setText(PrefsManager.getWebrtcStreamUrl(this))
        etVideoCallUrl.setText(PrefsManager.getVideoCallUrl(this))
        etStunUrl.setText(PrefsManager.getStunUrl(this))
        etTurnUrl.setText(PrefsManager.getTurnUrl(this))
        etTurnUser.setText(PrefsManager.getTurnUser(this))
        etTurnPass.setText(PrefsManager.getTurnPass(this))
        etWozUrl.setText(PrefsManager.getWozUrl(this))
    }

    private fun saveSettings() {
        PrefsManager.setHttpUrl(this, etHttpUrl.text.toString().trim())
        PrefsManager.setWsAudioUrl(this, etWsAudioUrl.text.toString().trim())
        PrefsManager.setWebrtcStreamUrl(this, etWebrtcStreamUrl.text.toString().trim())
        PrefsManager.setVideoCallUrl(this, etVideoCallUrl.text.toString().trim())
        PrefsManager.setStunUrl(this, etStunUrl.text.toString().trim())
        PrefsManager.setTurnUrl(this, etTurnUrl.text.toString().trim())
        PrefsManager.setTurnUser(this, etTurnUser.text.toString().trim())
        PrefsManager.setTurnPass(this, etTurnPass.text.toString().trim())
        PrefsManager.setWozUrl(this, etWozUrl.text.toString().trim())
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun resetDefaults() {
        etHttpUrl.setText(PrefsManager.DEFAULT_HTTP_URL)
        etWsAudioUrl.setText(PrefsManager.DEFAULT_WS_AUDIO_URL)
        etWebrtcStreamUrl.setText(PrefsManager.DEFAULT_WEBRTC_STREAM_URL)
        etVideoCallUrl.setText(PrefsManager.DEFAULT_VIDEO_CALL_URL)
        etStunUrl.setText(PrefsManager.DEFAULT_STUN_URL)
        etTurnUrl.setText(PrefsManager.DEFAULT_TURN_URL)
        etTurnUser.setText(PrefsManager.DEFAULT_TURN_USER)
        etTurnPass.setText(PrefsManager.DEFAULT_TURN_PASS)
        etWozUrl.setText(PrefsManager.DEFAULT_WOZ_URL)
        saveSettings()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
