package com.example.videostreaming

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText

    companion object {
        const val EXTRA_SERVER_IP = "server_ip"
        const val EXTRA_SERVER_PORT = "server_port"
        private const val PREF_NAME = "video_streaming_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServerIp = findViewById(R.id.etServerIp)
        etServerPort = findViewById(R.id.etServerPort)

        // 從 SharedPreferences 載入已儲存的 IP 和 Port
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        etServerIp.setText(prefs.getString("server_ip", "140.112.92.133"))
        etServerPort.setText(prefs.getString("server_port", "6868"))

        // 找到名為 'btnStart' 的 Button
        val btnStart: Button = findViewById(R.id.btnStart)

        // 設定點擊事件監聽器
        btnStart.setOnClickListener {
            // 儲存 IP 和 Port 到 SharedPreferences
            saveServerConfig()

            // 檢查是否已授予相機權限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                launchCameraActivity()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            }
        }
    }

    private fun saveServerConfig() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("server_ip", etServerIp.text.toString().trim())
            .putString("server_port", etServerPort.text.toString().trim())
            .apply()
    }

    private fun launchCameraActivity() {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra(EXTRA_SERVER_IP, etServerIp.text.toString().trim())
            putExtra(EXTRA_SERVER_PORT, etServerPort.text.toString().trim())
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCameraActivity()
        }
    }
}