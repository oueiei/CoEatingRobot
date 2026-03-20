package com.example.ntldemo

import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*

class VideoCallFragment : Fragment() {

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var isCameraOn = true
    private var isMuted = false

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private val rootEglBase: EglBase = EglBase.create()
    private val handler = Handler(Looper.getMainLooper())

    // UI 元件
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var ivRobotFaceMain: ImageView
    private lateinit var ivRobotFaceLocal: ImageView
    private lateinit var btnToggleCamera: Button
    private lateinit var btnToggleMute: Button
    private lateinit var btnHangUp: Button
    private lateinit var tvCallStatus: TextView

    // WebSocket
    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

    // 通話參數
    private var peerId: String = ""
    private var myId: String = ""
    private var isCaller: Boolean = false
    private val websocketUrl = "wss://sociallab.duckdns.org/ntl_demo/"

    companion object {
        private const val TAG = "VideoCallFragment"
        fun newInstance(peerId: String, myId: String, isCaller: Boolean): VideoCallFragment {
            val fragment = VideoCallFragment()
            val args = Bundle().apply {
                putString("PEER_ID", peerId)
                putString("MY_ID", myId)
                putBoolean("IS_CALLER", isCaller)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            peerId = it.getString("PEER_ID", "")
            myId = it.getString("MY_ID", "")
            isCaller = it.getBoolean("IS_CALLER", false)
        }
        Log.d(TAG, "onCreate: Fragment created. My ID: $myId, Peer ID: $peerId, Is Caller: $isCaller")

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView: Layout is being inflated.")
        return inflater.inflate(R.layout.fragment_video_call, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        initWebRTC()
//        initWebSocket()

        // 根據角色決定是否啟動攝影機
        if (myId == "AI讀") {
            Log.d(TAG, "onViewCreated: My role is AI讀, starting local video and creating offer.")
            startLocalVideo()
            view.postDelayed({ createOffer() }, 2000)
            startRepeatingMotion()
        }
    }

    private fun initViews(view: View) {
        Log.d(TAG, "initViews: UI components initialized.")
        remoteView = view.findViewById(R.id.remoteView)
        localView = view.findViewById(R.id.localView)
        ivRobotFaceMain = view.findViewById(R.id.ivRobotFace_main)
        ivRobotFaceLocal = view.findViewById(R.id.ivRobotFace_local)
        btnToggleCamera = view.findViewById(R.id.btnToggleCamera)
        btnToggleMute = view.findViewById(R.id.btnToggleMute)
        btnHangUp = view.findViewById(R.id.btnHangUp)
        tvCallStatus = view.findViewById(R.id.tvCallStatus)

        tvCallStatus.text = if (isCaller) "撥打視訊: $peerId" else "接收視訊: $myId"

        if (myId == "AI讀") {
            // AI讀：
            // 大視窗顯示圖片 (ivRobotFace_main)
            remoteView.visibility = View.GONE
            ivRobotFaceMain.visibility = View.VISIBLE
            // 小視窗顯示自己的鏡頭影像 (localView)
            localView.visibility = View.VISIBLE
            ivRobotFaceLocal.visibility = View.GONE
        } else {
            // AI閱：
            // 大視窗顯示 AI讀 的鏡頭影像 (remoteView)
            remoteView.visibility = View.VISIBLE
            ivRobotFaceMain.visibility = View.GONE
            // 小視窗顯示圖片 (ivRobotFace_local)
            localView.visibility = View.GONE
            ivRobotFaceLocal.visibility = View.VISIBLE
        }

        btnToggleCamera.setOnClickListener { toggleCamera() }
        btnToggleMute.setOnClickListener { toggleMute() }
        btnHangUp.setOnClickListener { hangUp() }
    }

    private fun initWebRTC() {
        Log.d(TAG, "initWebRTC: Initializing WebRTC components.")
        val options = PeerConnectionFactory.InitializationOptions.builder(requireContext())
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        Log.d(TAG, "initWebRTC: PeerConnectionFactory initialized.")

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        // 初始化兩個 SurfaceViewRenderer
        remoteView.init(rootEglBase.eglBaseContext, null)
        localView.init(rootEglBase.eglBaseContext, null)

        createPeerConnection()
    }

    private fun createPeerConnection() {
        Log.d(TAG, "createPeerConnection: Creating new PeerConnection.")
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                Log.d(TAG, "onIceCandidate: Received ICE candidate: $candidate")
                candidate?.let { sendIceCandidate(it) }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ICE Connection State changed to $state")
                activity?.runOnUiThread {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            tvCallStatus.text = "視訊連接成功"
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            tvCallStatus.text = "連接中斷"
                        }
                        else -> {}
                    }
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "onAddTrack: Remote track added. Track kind: ${receiver?.track()?.kind()}")
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    activity?.runOnUiThread {
                        // 不論角色是誰，將遠端視訊渲染到全螢幕的 remoteView
                        remoteVideoTrack?.addSink(remoteView)
                    }
                }
            }

            override fun onDataChannel(p0: DataChannel?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ICE Gathering State changed to $state")
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Log.d(TAG, "onSignalingChange: Signaling State changed to $state")
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: Renegotiation needed.")
            }
            override fun onAddStream(p0: MediaStream?) {}
        })
    }

    private fun performRobotAction(motionId: String) {
        (activity as? MainActivity)?.performMotion(motionId)
    }
    private fun startRepeatingMotion() {
        handler.postDelayed({},3000)
        performRobotAction("666_BA_LookU023")
        performRobotAction("666_WO_Handling")
        // 例如每5秒執行一次動作
//        handler.postDelayed({
//            startRepeatingMotion() // 遞歸呼叫實現重複
//        }, 5000)
    }

    private fun stopRepeatingMotion() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun startLocalVideo() {
        Log.d(TAG, "startLocalVideo: Starting local video capture.")
        videoCapturer = createCameraCapturer()
        if (videoCapturer == null) return

        val videoSource = peerConnectionFactory.createVideoSource(false)
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name, rootEglBase.eglBaseContext
        )

        videoCapturer?.initialize(surfaceTextureHelper, requireContext(), videoSource.capturerObserver)
        videoCapturer?.startCapture(640, 480, 30)
        Log.d(TAG, "startLocalVideo: Camera capture started.")

        localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)

        // 只有 AI讀 才將本地視訊渲染到小視窗
        if (myId == "AI讀") {
            localVideoTrack?.addSink(localView)
        }

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource)
        Log.d(TAG, "startLocalVideo: Local video and audio tracks created.")

        peerConnection?.addTrack(localVideoTrack, listOf("local_stream"))
        peerConnection?.addTrack(audioTrack, listOf("local_stream"))
        Log.d(TAG, "startLocalVideo: Local tracks added to PeerConnection.")
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(requireContext())
        return enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?.let {
                Log.d(TAG, "createCameraCapturer: Using front-facing camera: $it")
                enumerator.createCapturer(it, null)
            }
    }

    private fun sendMessage(message: JSONObject) {
        (activity as? MainActivity)?.webSocket?.send(message.toString())
    }

    fun handleSignalingMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            val fromId = json.getString("from") // 提取傳送方的ID
            Log.d(TAG, "handleSignalingMessage: Received signaling message type: $type from $fromId")
            when (type) {
                "offer" -> {
                    val sdp = json.getString("sdp")
                    val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                    // 更新 peerId
                    peerId = fromId
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() { createAnswer() }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, offer)
                }
                "answer" -> {
                    val sdp = json.getString("sdp")
                    val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {}
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, answer)
                }
                "ice_candidate" -> {
                    val candidate = IceCandidate(
                        json.getString("sdpMid"),
                        json.getInt("sdpMLineIndex"),
                        json.getString("candidate")
                    )
                    peerConnection?.addIceCandidate(candidate)
                }
            }
        } catch (e: Exception) {
            Log.e("VideoCall", "Signal error", e)
        }
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { sendOffer(sdp!!) }
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
                    override fun onSetSuccess() { sendAnswer(sdp!!) }
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
        sendMessage(message)
    }

    private fun sendAnswer(answer: SessionDescription) {
        val message = JSONObject().apply {
            put("type", "answer")
            put("from", myId)
            put("to", peerId)
            put("sdp", answer.description)
        }
        sendMessage(message)
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val message = JSONObject().apply {
            put("type", "ice_candidate")
            put("from", myId)
            put("to", peerId) // 確保這裡使用的是正確的 peerId
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        Log.d(TAG, "sendIceCandidate: Sending ICE candidate from $myId to $peerId.")
        sendMessage(message)
    }

    private fun toggleCamera() {
        if (myId == "AI讀") {
            // AI讀 控制實際攝影機
            if (isCameraOn) {
                videoCapturer?.stopCapture()
                btnToggleCamera.setBackgroundResource(R.drawable.videocam_off_24px)
            } else {
                videoCapturer?.startCapture(640, 480, 30)
                btnToggleCamera.setBackgroundResource(R.drawable.videocam_24px)
            }
            isCameraOn = !isCameraOn
        } else {
            // AI閱 切換機器人頭像顯示
            if (isCameraOn) {
                videoCapturer?.stopCapture()
                btnToggleCamera.setBackgroundResource(R.drawable.videocam_off_24px)
            } else {
                videoCapturer?.startCapture(640, 480, 30)
                btnToggleCamera.setBackgroundResource(R.drawable.videocam_24px)
            }
            isCameraOn = !isCameraOn
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        btnToggleMute.setBackgroundResource(
            if (isMuted) R.drawable.mic_off_24px else R.drawable.mic_24px
        )
        // 音頻軌道控制可在此添加
    }

    private fun hangUp() {
        val message = JSONObject().apply {
            put("type", "call_ended")
            put("from", myId)
            put("to", peerId)
        }

        // 通知 MainActivity
        (activity as? MainActivity)?.webSocket?.send(message.toString())
        (activity as? MainActivity)?.onVideoCallEnded()
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Fragment is being destroyed. Cleaning up WebRTC resources.")
        super.onDestroyView()
        (activity as? MainActivity)?.onVideoCallEnded()
        stopRepeatingMotion()
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localVideoTrack?.dispose()
        remoteVideoTrack?.dispose()
        peerConnection?.close()
        peerConnectionFactory.dispose()
        rootEglBase.release()
    }
}