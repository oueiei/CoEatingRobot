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

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var frontCamera = true
    private var isStreaming = false
    private var isCameraOn = true

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private val rootEglBase: EglBase = EglBase.create()

    // UI 元件
    private lateinit var btnToggleCamera: Button

    private lateinit var btnToggleVideo: Button
    private lateinit var btnStream: Button
    private lateinit var localView: SurfaceViewRenderer

    // WebSocket
    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()
    private lateinit var websocketUrl: String
    private lateinit var serverIp: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // 從 Intent 取得 IP 和 Port
        serverIp = intent.getStringExtra(MainActivity.EXTRA_SERVER_IP) ?: "140.112.92.133"
        val serverPort = intent.getStringExtra(MainActivity.EXTRA_SERVER_PORT) ?: "6868"
        websocketUrl = "ws://$serverIp:$serverPort"

        // 初始化 UI
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
        btnToggleVideo = findViewById(R.id.btnToggleVideo)
        btnStream = findViewById(R.id.btnStream)
        localView = findViewById(R.id.localView)

        initWebRTC()
        initWebSocket()

        // 權限檢查
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.d("Permission", "相機權限已有，啟動相機")
            startCamera()
        } else {
            Log.w("Permission", "尚未取得相機權限，開始請求")
            // 跳出請求權限的視窗
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1001)
        }
        
        // UI 設定

        btnToggleCamera.setOnClickListener { toggleCamera() }
        btnToggleVideo.setOnClickListener { toggleVideoVisibility() }
        btnStream.setOnClickListener { if (isStreaming) stopStream() else startStream() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d("Permission", "相機權限已取得，啟動相機")
                // 用戶按下 "允許" 後，在這裡啟動相機
                startCamera()
            } else {
                Log.e("Permission", "相機權限被拒絕")
                // 你可以在這裡向用戶顯示一個提示
            }
        }
    }

    // 相機操作相關

    private fun startCamera() {
        if (!isCameraOn) return

        videoCapturer = createCameraCapturer()

        val videoSource = peerConnectionFactory.createVideoSource(false)
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name, rootEglBase.eglBaseContext
        )

        videoCapturer?.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        videoCapturer?.startCapture(640, 480, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
        localVideoTrack?.addSink(localView)
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val camera2Enumerator = Camera2Enumerator(this)
        val deviceNames = camera2Enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (frontCamera && camera2Enumerator.isFrontFacing(deviceName)) {
                return camera2Enumerator.createCapturer(deviceName, null)
            }
            if (!frontCamera && camera2Enumerator.isBackFacing(deviceName)) {
                return camera2Enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    private fun switchCamera() {
        frontCamera = !frontCamera
        if (isCameraOn) {
            stopCamera()
            startCamera()
        }
    }

    private fun toggleCamera() {
        if (isCameraOn) {
            stopCamera()
            btnToggleCamera.text = "開啟鏡頭"
        } else {
            startCamera()
            btnToggleCamera.text = "關閉鏡頭"
        }
        isCameraOn = !isCameraOn
    }

    private fun stopCamera() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        localVideoTrack?.dispose()
        localVideoTrack = null
    }

    private fun toggleVideoVisibility() {
        if (localView.visibility == View.VISIBLE) {
            localView.visibility = View.GONE
            btnToggleVideo.text = "顯示畫面"
        } else {
            localView.visibility = View.VISIBLE
            btnToggleVideo.text = "隱藏畫面"
        }
    }

    private fun initWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        localView.init(rootEglBase.eglBaseContext, null)
    }


    private fun initWebSocket() {
        Log.d("WebSocket", "正在連接: $websocketUrl")
        val request = Request.Builder().url(websocketUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "WebSocket 已連接")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "收到訊息: $text")

                try {
                    val message = JSONObject(text)

                    // 處理 SDP Answer
                    if (message.has("sdp")) {
                        val sdp = message.getJSONObject("sdp")
                        if (sdp.getString("type") == "answer") {
                            Log.d("WebRTC", "正在設定 Remote Answer SDP")
                            val sessionDescription = SessionDescription(
                                SessionDescription.Type.ANSWER,
                                sdp.getString("sdp")
                            )
                            peerConnection?.setRemoteDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    Log.d("WebRTC", "Remote SDP set 成功")
                                }
                                override fun onCreateFailure(s: String?) {
                                    Log.e("WebRTC", "Remote SDP onCreateFailure: $s")
                                }
                                override fun onSetFailure(s: String?) {
                                    Log.e("WebRTC", "Remote SDP onSetFailure: $s")
                                }
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                            }, sessionDescription)
                        }
                    }

                    // 處理 ICE Candidate
                    if (message.has("iceCandidate")) {
                        val candidateData = message.getJSONObject("iceCandidate")
                        val candidate = IceCandidate(
                            candidateData.getString("sdpMid"),
                            candidateData.getInt("sdpMLineIndex"),
                            candidateData.getString("candidate")
                        )
                        Log.d("WebRTC", "收到並添加遠端 ICE Candidate")
                        peerConnection?.addIceCandidate(candidate)
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "解析訊息時出錯: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "連接失敗: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "連接已關閉: $reason")
            }
        })
    }

    private fun startStream() {
        Log.d("WebSocket", "開始串流，WebSocket 狀態檢查...")

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:$serverIp:3478")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:$serverIp:3478")
                .setUsername("hcc")
                .setPassword("j0207")
                .createIceServer()
        )

        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, object : PeerConnection.Observer {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                // Trickle ICE 模式:即時傳送每個 candidate
                iceCandidate?.let {
                    Log.d("WebRTC", "發送 ICE Candidate: ${it.sdp}")
                    val candidate = JSONObject().apply {
                        put("candidate", it.sdp)
                        put("sdpMid", it.sdpMid)
                        put("sdpMLineIndex", it.sdpMLineIndex)
                    }
                    val message = JSONObject().put("iceCandidate", candidate)
                    webSocket.send(message.toString())
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d("WebRTC", "ICE State: $state")
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    Log.e("WebRTC", "ICE Connection FAILED!")
                }
                if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                    Log.i("WebRTC", "🎉🎉🎉 ICE 連接成功！ 🎉🎉🎉")
                }
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d("WebRTC", "ICE Gathering: $state")
                // Trickle ICE 模式:不需要等待收集完成
            }
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        // 使用 addTrack 而不是 addStream
        localVideoTrack?.let {
            peerConnection?.addTrack(it, listOf("local_stream"))
        }

        // 創建 Offer
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        // Trickle ICE 模式:立即發送 Offer (不等 ICE 收集)
                        Log.d("WebRTC", "立即發送 Offer SDP")
                        sdp?.let {
                            val offer = JSONObject().apply {
                                put("type", it.type.canonicalForm())
                                put("sdp", it.description)
                            }
                            val message = JSONObject().put("sdp", offer)
                            webSocket.send(message.toString())
                        }
                    }
                    override fun onSetFailure(s: String?) {
                        Log.e("WebRTC", "setLocalDescription 失敗: $s")
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(s: String?) {
                Log.e("WebRTC", "createOffer 失敗: $s")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
        isStreaming = true
        btnStream.text = "停止串流"
    }

    private fun stopStream() {
        peerConnection?.close()
        peerConnection = null
        isStreaming = false
        btnStream.text = "開始串流"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        stopStream()
        webSocket.close(1000, "App closed")
        peerConnectionFactory.dispose()
        rootEglBase.release()
    }
}