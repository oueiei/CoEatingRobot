package com.example.videostreaming

import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SignalingClient(private val serverUrl: String = "ws://192.168.111.167:8080") {

    private var webSocket: WebSocket? = null
    private var listener: SignalingListener? = null

    interface SignalingListener {
        fun onOfferReceived(offer: SessionDescription)
        fun onAnswerReceived(answer: SessionDescription)
        fun onIceCandidateReceived(candidate: IceCandidate)
    }

    fun connect(listener: SignalingListener) {
        this.listener = listener
        val client = OkHttpClient()
        val request = Request.Builder().url(serverUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
        })
    }

    fun sendOffer(offer: SessionDescription) {
        val json = JSONObject().apply {
            put("type", "offer")
            put("sdp", offer.description)
        }
        webSocket?.send(json.toString())
    }

    fun sendAnswer(answer: SessionDescription) {
        val json = JSONObject().apply {
            put("type", "answer")
            put("sdp", answer.description)
        }
        webSocket?.send(json.toString())
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("type", "ice")
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        webSocket?.send(json.toString())
    }

    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.getString("type")) {
                "offer" -> {
                    val offer = SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp"))
                    listener?.onOfferReceived(offer)
                }
                "answer" -> {
                    val answer = SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"))
                    listener?.onAnswerReceived(answer)
                }
                "ice" -> {
                    val candidate = IceCandidate(
                        json.getString("sdpMid"),
                        json.getInt("sdpMLineIndex"),
                        json.getString("candidate")
                    )
                    listener?.onIceCandidateReceived(candidate)
                }
            }
        } catch (e: Exception) {
            // 錯誤處理
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "")
    }
}