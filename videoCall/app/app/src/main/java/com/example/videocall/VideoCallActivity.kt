package com.example.videocall

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.Intent
import android.content.pm.PackageManager

class VideoCallActivity : AppCompatActivity() {

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var frontCamera = true
    private var isCameraOn = true
    private var isMuted = false

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private val rootEglBase: EglBase = EglBase.create()

    // UI 元件
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var btnToggleCamera: Button
    private lateinit var btnToggleMute: Button
    private lateinit var btnHangUp: Button
    private lateinit var tvCallStatus: TextView

    // WebSocket for signaling
    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

    // 通話參數
    private lateinit var peerId: String
    private lateinit var myId: String
    private var isCaller: Boolean = false
    private lateinit var websocketUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)

        // 獲取傳入參數
        peerId = intent.getStringExtra("PEER_ID") ?: ""
        myId = intent.getStringExtra("MY_ID") ?: ""
        isCaller = intent.getBooleanExtra("IS_CALLER", false)
        websocketUrl = intent.getStringExtra("WEBSOCKET_URL") ?: ""

        Log.d("VideoCall", "WebSocket URL: $websocketUrl")
        Log.d("VideoCall", "嘗試連接並註冊用戶: $myId")

        initViews()
        initWebRTC()
        initWebSocket()
        Log.d("WebRTC", "開始啟動相機")
        startLocalVideo()

        if (isCaller) {
            // 主呼方創建 offer
            createOffer()
        }
    }

    private fun initViews() {
        localView = findViewById(R.id.localView)
        remoteView = findViewById(R.id.remoteView)
        btnToggleCamera = findViewById(R.id.btnToggleCamera)
        btnToggleMute = findViewById(R.id.btnToggleMute)
        btnHangUp = findViewById(R.id.btnHangUp)
        tvCallStatus = findViewById(R.id.tvCallStatus)

        tvCallStatus.text = if (isCaller) "撥打中: $peerId" else "通話中: $peerId"

        btnToggleCamera.setOnClickListener { toggleCamera() }
        btnToggleMute.setOnClickListener { toggleMute() }
        btnHangUp.setOnClickListener { hangUp() }
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
        remoteView.init(rootEglBase.eglBaseContext, null)

        createPeerConnection()
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                iceCandidate?.let {
                    sendIceCandidate(it)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                runOnUiThread {
                    Log.d("WebRTC", "ICE Connection State: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            tvCallStatus.text = "已連接: $peerId"
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            tvCallStatus.text = "連接中斷"
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            tvCallStatus.text = "連接失敗"
                        }
                        else -> {}
                    }
                }
            }

            override fun onAddTrack(rtpReceiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                Log.d("WebRTC", "onAddTrack 被調用")
                val track = rtpReceiver?.track()
                Log.d("WebRTC", "軌道類型: ${track?.kind()}")

                if (track is VideoTrack) {
                    Log.d("WebRTC", "收到視頻軌道，設置到 remoteView")
                    remoteVideoTrack = track
                    runOnUiThread {
                        remoteVideoTrack?.addSink(remoteView)
                        Log.d("WebRTC", "遠端視頻軌道已添加到視圖")
                    }
                }
            }
            override fun onAddStream(mediaStream: MediaStream?) {
                Log.d("WebRTC", "onAddStream")
                mediaStream?.videoTracks?.let { videoTracks ->
                    if (videoTracks.isNotEmpty()) {
                        remoteVideoTrack = videoTracks[0]
                        runOnUiThread {
                            remoteVideoTrack?.addSink(remoteView)
                        }
                    }
                }
            }

            override fun onDataChannel(p0: DataChannel?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
        })
    }

    private fun startLocalVideo() {
        Log.d("Camera", "=== 開始相機初始化 ===")
        Log.d("Camera", "前鏡頭模式: $frontCamera")
        Log.d("Camera", "相機開啟狀態: $isCameraOn")

        if (!isCameraOn) {
            Log.d("Camera", "相機關閉，跳過初始化")
            return
        }

        // 檢查權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("Camera", "相機權限未授予")
            return
        }
        videoCapturer = createCameraCapturer()
        Log.d("Camera", "VideoCapturer 創建結果: ${videoCapturer != null}")

        if (videoCapturer == null) {
            Log.e("Camera", "無法創建相機捕獲器")
            return
        }

        val videoSource = peerConnectionFactory.createVideoSource(false)
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name, rootEglBase.eglBaseContext
        )

        videoCapturer?.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        Log.d("Camera", "VideoCapturer 初始化完成")

        videoCapturer?.startCapture(640, 480, 30)
        Log.d("Camera", "VideoCapturer 開始捕獲")

        localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
        Log.d("Camera", "本地視頻軌道創建: ${localVideoTrack != null}")
        localView.setMirror(true)

        localVideoTrack?.addSink(localView)

        Log.d("Camera", "視頻軌道已添加到本地視圖")

        // 添加音頻軌道
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource)


        peerConnection?.addTrack(localVideoTrack, listOf("local_stream"))
        Log.d("WebRTC", "已添加本地視頻軌道")
        peerConnection?.addTrack(audioTrack, listOf("local_stream"))
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val camera2Enumerator = Camera2Enumerator(this)
        val deviceNames = camera2Enumerator.deviceNames

        Log.d("Camera", "可用相機數量: ${deviceNames.size}")
        for (name in deviceNames) {
            Log.d("Camera", "相機: $name, 前鏡頭: ${camera2Enumerator.isFrontFacing(name)}")
        }

        for (deviceName in deviceNames) {
            if (frontCamera && camera2Enumerator.isFrontFacing(deviceName)) {
                Log.d("Camera", "選擇前鏡頭: $deviceName")
                return camera2Enumerator.createCapturer(deviceName, null)
            }
            if (!frontCamera && camera2Enumerator.isBackFacing(deviceName)) {
                Log.d("Camera", "選擇後鏡頭: $deviceName")
                return camera2Enumerator.createCapturer(deviceName, null)
            }
        }

        Log.e("Camera", "找不到合適的相機")
        return null
    }

    private fun initWebSocket() {
        Log.d("WebSocket", "嘗試連接: $websocketUrl")
        val request = Request.Builder().url(websocketUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "VideoCallActivity WebSocket 已連接")

                // 重新註冊用戶 ID
                val registerMessage = JSONObject().apply {
                    put("type", "register")
                    put("id", myId)
                }
                webSocket.send(registerMessage.toString())
                Log.d("WebSocket", "VideoCallActivity 已註冊用戶: $myId")
            }


            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "收到訊息: $text")
                handleSignalingMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "WebSocket 連接失敗: ${t.message}")
            }
        })
    }

    private fun handleSignalingMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")

            Log.d("Signaling", "收到信令: $type")
            Log.d("Signaling", "完整訊息: $message")

            when (type) {
                "offer" -> {
                    Log.d("Signaling", "處理 offer")
                    val sdp = json.getString("sdp")
                    val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            createAnswer()
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, offer)
                }

                "answer" -> {
                    Log.d("Signaling", "處理 answer")
                    val sdp = json.getString("sdp")
                    val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d("WebRTC", "Remote answer set successfully")
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, answer)
                }

                "ice_candidate" -> {
                    Log.d("Signaling", "處理 ICE candidate")
                    val candidate = IceCandidate(
                        json.getString("sdpMid"),
                        json.getInt("sdpMLineIndex"),
                        json.getString("candidate")
                    )
                    peerConnection?.addIceCandidate(candidate)
                }

                "call_ended" -> {
                    runOnUiThread {
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Signaling", "信令處理錯誤", e)
        }
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        Log.d("WebRTC", "創建 offer，本地軌道數: ${peerConnection?.localDescription}")

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d("WebRTC", "Offer 創建成功")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        sendOffer(sdp!!)
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        sendAnswer(sdp!!)
                    }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun sendOffer(offer: SessionDescription) {
        val message = JSONObject().apply {
            put("type", "offer")
            put("from", myId)
            put("to", peerId)
            put("sdp", offer.description)
        }
        webSocket.send(message.toString())
    }

    private fun sendAnswer(answer: SessionDescription) {
        val message = JSONObject().apply {
            put("type", "answer")
            put("from", myId)
            put("to", peerId)
            put("sdp", answer.description)
        }
        webSocket.send(message.toString())
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val message = JSONObject().apply {
            put("type", "ice_candidate")
            put("from", myId)
            put("to", peerId)
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        webSocket.send(message.toString())
    }

    private fun toggleCamera() {
        if (isCameraOn) {
            videoCapturer?.stopCapture()
            btnToggleCamera.text = "開啟鏡頭"
            localView.visibility = View.GONE
        } else {
            videoCapturer?.startCapture(640, 480, 30)
            btnToggleCamera.text = "關閉鏡頭"
            localView.visibility = View.VISIBLE
        }
        isCameraOn = !isCameraOn
    }

    private fun toggleMute() {
        // 實作靜音功能
        isMuted = !isMuted
        btnToggleMute.text = if (isMuted) "取消靜音" else "靜音"
        // 這裡可以添加實際的音頻軌道控制
    }

    private fun hangUp() {
        // 發送通話結束訊息
        val message = JSONObject().apply {
            put("type", "call_ended")
            put("from", myId)
            put("to", peerId)
        }
        webSocket.send(message.toString())

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        val intent = Intent("VIDEO_CALL_ENDED")
        sendBroadcast(intent)

        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localVideoTrack?.dispose()
        remoteVideoTrack?.dispose()
        peerConnection?.close()
        peerConnectionFactory.dispose()
        rootEglBase.release()
        webSocket.close(1000, "Call ended")
    }
}