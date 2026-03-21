package com.example.videostreaming.managers

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*

// ==================== WebRTC Manager ====================
class WebRTCManager(
    private val context: android.content.Context,
    private val cameraManager: CameraManager
) {
    private var peerConnection: PeerConnection? = null
    var isStreaming = false
        private set

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:140.112.92.133:3478").createIceServer(),
        PeerConnection.IceServer.builder("turn:140.112.92.133:3478")
            .setUsername("hcc")
            .setPassword("j0207")
            .createIceServer()
    )

    fun startStream(signalingClient: SignalingClient) {
        peerConnection = cameraManager.getPeerConnectionFactory()
            .createPeerConnection(iceServers, createPeerConnectionObserver(signalingClient))

        cameraManager.getVideoTrack()?.let {
            peerConnection?.addTrack(it, listOf("local_stream"))
        }

        peerConnection?.createOffer(createOfferObserver(signalingClient), MediaConstraints())
        isStreaming = true
    }

    fun stopStream() {
        peerConnection?.close()
        peerConnection = null
        isStreaming = false
    }

    fun handleSignalingMessage(message: JSONObject) {
        try {
            when {
                message.has("sdp") -> handleSdpMessage(message.getJSONObject("sdp"))
                message.has("iceCandidate") -> handleIceCandidate(message.getJSONObject("iceCandidate"))
            }
        } catch (e: Exception) {
            Log.e("WebRTC", "Error handling message", e)
        }
    }

    private fun handleSdpMessage(sdp: JSONObject) {
        if (sdp.getString("type") == "answer") {
            val sessionDescription = SessionDescription(
                SessionDescription.Type.ANSWER,
                sdp.getString("sdp")
            )
            peerConnection?.setRemoteDescription(SimpleSdpObserver("SetRemoteAnswer"), sessionDescription)
        }
    }

    private fun handleIceCandidate(candidateData: JSONObject) {
        val candidate = IceCandidate(
            candidateData.getString("sdpMid"),
            candidateData.getInt("sdpMLineIndex"),
            candidateData.getString("candidate")
        )
        peerConnection?.addIceCandidate(candidate)
    }

    private fun createPeerConnectionObserver(signalingClient: SignalingClient) = 
        object : PeerConnection.Observer {
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                iceCandidate?.let { signalingClient.sendIceCandidate(it) }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d("WebRTC", "ICE State: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED ->
                        Log.i("WebRTC", "🎉 ICE Connected!")
                    PeerConnection.IceConnectionState.FAILED ->
                        Log.e("WebRTC", "ICE Failed!")
                    else -> {}
                }
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        }

    private fun createOfferObserver(signalingClient: SignalingClient) = 
        object : SimpleSdpObserver("CreateOffer") {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver("SetLocalOffer"), sdp)
                sdp?.let { signalingClient.sendOffer(it) }
            }
        }

    fun release() {
        stopStream()
    }
}



// ==================== Helper Classes ====================
open class SimpleSdpObserver(private val tag: String) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {
        Log.d(tag, "CreateSuccess")
    }
    override fun onSetSuccess() {
        Log.d(tag, "SetSuccess")
    }
    override fun onCreateFailure(error: String?) {
        Log.e(tag, "CreateFailure: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e(tag, "SetFailure: $error")
    }
}