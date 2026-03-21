package com.example.videostreaming

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*

class CameraActivity : AppCompatActivity() {
    
    private lateinit var cameraManager: CameraManager
    private lateinit var webRTCManager: WebRTCManager
    private lateinit var signalingClient: SignalingClient
    
    // UI
    private lateinit var btnToggleCamera: Button
    private lateinit var btnToggleVideo: Button
    private lateinit var btnStream: Button
    private lateinit var localView: SurfaceViewRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        initUI()
        initManagers()
        requestCameraPermissionIfNeeded()
        setupListeners()
    }

    private fun initUI() {
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
        btnToggleVideo = findViewById(R.id.btnToggleVideo)
        btnStream = findViewById(R.id.btnStream)
        localView = findViewById(R.id.localView)
    }

    private fun initManagers() {
        cameraManager = CameraManager(this, localView)
        webRTCManager = WebRTCManager(this, cameraManager)
        signalingClient = SignalingClient("ws://140.112.92.133:6868") { message ->
            webRTCManager.handleSignalingMessage(message)
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) 
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraManager.startCamera()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1001)
        }
    }

    private fun setupListeners() {
        btnToggleCamera.setOnClickListener {
            cameraManager.toggleCamera()
            btnToggleCamera.text = if (cameraManager.isCameraOn) "關閉鏡頭" else "開啟鏡頭"
        }

        btnToggleVideo.setOnClickListener {
            localView.visibility = if (localView.visibility == View.VISIBLE) {
                btnToggleVideo.text = "顯示畫面"
                View.GONE
            } else {
                btnToggleVideo.text = "隱藏畫面"
                View.VISIBLE
            }
        }

        btnStream.setOnClickListener {
            if (webRTCManager.isStreaming) {
                webRTCManager.stopStream()
                btnStream.text = "開始串流"
            } else {
                webRTCManager.startStream(signalingClient)
                btnStream.text = "停止串流"
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<out String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() 
            && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraManager.startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.release()
        webRTCManager.release()
        signalingClient.disconnect()
    }
}