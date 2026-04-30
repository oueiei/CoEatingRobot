package com.example.socialroboticslab

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.socialroboticslab.http.HttpChatActivity
import com.example.socialroboticslab.http.HttpChatActivity_CEA
import com.example.socialroboticslab.streaming.VideoStreamActivity
import com.example.socialroboticslab.util.PrefsManager
import com.example.socialroboticslab.videocall.VideoCallLobbyActivity
import com.example.socialroboticslab.websocket.AudioStreamActivity
import com.example.socialroboticslab.woz.WozRobotActivity

class UnitEntryActivity : AppCompatActivity() {

    private data class UnitConfig(
        val title: String,
        val description: String,
        val loadUrl: (Context) -> String,
        val saveUrl: (Context, String) -> Unit,
        val targetActivity: Class<out AppCompatActivity>,
        val supportsAutoHello: Boolean = false,
    )

    companion object {
        private const val EXTRA_UNIT_ID = "unit_id"

        const val EXTRA_AUTO_HELLO = "extra_auto_hello"
        const val EXTRA_INITIAL_MESSAGE = "extra_initial_message"

        const val UNIT_HTTP_CHAT = "http_chat"
        const val UNIT_WS_AUDIO = "ws_audio"
        const val UNIT_VIDEO_STREAM = "video_stream"
        const val UNIT_VIDEO_CALL = "video_call"
        const val UNIT_WOZ = "woz"

        const val UNIT_HTTP_CHAT_CEA = "http_chat_cea"

        fun createIntent(context: Context, unitId: String): Intent =
            Intent(context, UnitEntryActivity::class.java).putExtra(EXTRA_UNIT_ID, unitId)
    }

    private lateinit var tvUnitTitle: TextView
    private lateinit var tvUnitDescription: TextView
    private lateinit var etServerUrl: EditText
    private lateinit var switchAutoHello: SwitchCompat
    private lateinit var etInitialMessage: EditText
    private lateinit var btnStart: Button

    private lateinit var config: UnitConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unit_entry)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "單元啟動設定"

        tvUnitTitle = findViewById(R.id.tvUnitTitle)
        tvUnitDescription = findViewById(R.id.tvUnitDescription)
        etServerUrl = findViewById(R.id.etServerUrl)
        switchAutoHello = findViewById(R.id.switchAutoHello)
        etInitialMessage = findViewById(R.id.etInitialMessage)
        btnStart = findViewById(R.id.btnStartUnit)

        config = resolveConfig(intent.getStringExtra(EXTRA_UNIT_ID))

        tvUnitTitle.text = config.title
        tvUnitDescription.text = config.description
        etServerUrl.setText(config.loadUrl(this))

        if (config.supportsAutoHello) {
            switchAutoHello.isChecked = true
        } else {
            switchAutoHello.isEnabled = false
            switchAutoHello.isChecked = false
            switchAutoHello.text = "此單元尚未接自動 hello"
            etInitialMessage.isEnabled = false
        }

        switchAutoHello.setOnCheckedChangeListener { _, isChecked ->
            etInitialMessage.isEnabled = isChecked
        }
        etInitialMessage.isEnabled = switchAutoHello.isChecked

        btnStart.setOnClickListener { launchUnit() }
    }

    private fun resolveConfig(unitId: String?): UnitConfig {
        return when (unitId) {
            UNIT_HTTP_CHAT -> UnitConfig(
                title = "Ch2 HTTP Chat",
                description = "先確認本次要連的 HTTP server，再決定是否先送 hello 讓後端主動開場。",
                loadUrl = { PrefsManager.getHttpUrl(it) },
                saveUrl = { ctx, value -> PrefsManager.setHttpUrl(ctx, value) },
                targetActivity = HttpChatActivity::class.java,
                supportsAutoHello = true,
            )
            UNIT_WS_AUDIO -> UnitConfig(
                title = "Ch3 WebSocket Audio",
                description = "先設定本次 WebSocket server 位址，再進入音訊串流測試。",
                loadUrl = { PrefsManager.getWsAudioUrl(it) },
                saveUrl = { ctx, value -> PrefsManager.setWsAudioUrl(ctx, value) },
                targetActivity = AudioStreamActivity::class.java,
            )
            UNIT_VIDEO_STREAM -> UnitConfig(
                title = "Ch4 Video Streaming",
                description = "先設定本次 WebRTC signaling server 位址，再開始推流。",
                loadUrl = { PrefsManager.getWebrtcStreamUrl(it) },
                saveUrl = { ctx, value -> PrefsManager.setWebrtcStreamUrl(ctx, value) },
                targetActivity = VideoStreamActivity::class.java,
            )
            UNIT_VIDEO_CALL -> UnitConfig(
                title = "Ch5 Video Call",
                description = "先確認視訊通話 server 位址，再進入 lobby。",
                loadUrl = { PrefsManager.getVideoCallUrl(it) },
                saveUrl = { ctx, value -> PrefsManager.setVideoCallUrl(ctx, value) },
                targetActivity = VideoCallLobbyActivity::class.java,
            )
            UNIT_WOZ -> UnitConfig(
                title = "Ch6 WoZ Controller",
                description = "先設定本次 WoZ server 位址，再註冊機器人與等待角色分配。",
                loadUrl = { PrefsManager.getWozUrl(it) },
                saveUrl = { ctx, value -> PrefsManager.setWozUrl(ctx, value) },
                targetActivity = WozRobotActivity::class.java,
            )
            UNIT_HTTP_CHAT_CEA -> UnitConfig(
                title = "Co-Eating Agent",
                description = "先確認本次要連的 HTTP server，再決定是否先送 hello 讓後端主動開場。",
                loadUrl = { PrefsManager.getHttpUrl_CEA(it) },
                saveUrl = { ctx, value -> PrefsManager.setHttpUrl_CEA(ctx, value) },
                targetActivity = HttpChatActivity_CEA::class.java,
                supportsAutoHello = true,
            )
            else -> error("Unknown unit id: $unitId")
        }
    }

    private fun launchUnit() {
        val serverUrl = etServerUrl.text.toString().trim()
        config.saveUrl(this, serverUrl)

        val launchIntent = Intent(this, config.targetActivity).apply {
            if (config.supportsAutoHello && switchAutoHello.isChecked) {
                putExtra(EXTRA_AUTO_HELLO, true)
                putExtra(
                    EXTRA_INITIAL_MESSAGE,
                    etInitialMessage.text.toString().trim().ifBlank { "hello" }
                )
            }
        }

        startActivity(launchIntent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
