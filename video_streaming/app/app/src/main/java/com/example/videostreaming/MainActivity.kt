package com.example.videostreaming

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

        // 找到名為 'btnStart' 的 Button
        val btnStart: Button = findViewById(R.id.btnStart)

        // 設定點擊事件監聽器
        btnStart.setOnClickListener {
            // 檢查是否已授予相機權限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                // 如果有權限，啟動 CameraActivity
                startActivity(Intent(this, CameraActivity::class.java))
            } else {
                // 如果沒有權限，請求權限
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 檢查權限請求結果
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 如果權限被授予，啟動 CameraActivity
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }
}