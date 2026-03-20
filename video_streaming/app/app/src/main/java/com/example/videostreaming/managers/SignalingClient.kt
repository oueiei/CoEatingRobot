package com.example.videostreaming.managers

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*

// ==================== Signaling Client ====================
class SignalingClient(
    private val url: String,
    private val onMessage: (JSONObject) -> Unit
) {
    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

    init {
        connect()
    }

    private fun connect() {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    onMessage(JSONObject(text))
                } catch (e: Exception) {
                    Log.e("WebSocket", "Parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Connection failed: ${t.message}")
            }
        })
    }

    fun sendOffer(sdp: SessionDescription) {
        val message = JSONObject().apply {
            put("sdp", JSONObject().apply {
                put("type", sdp.type.canonicalForm())
                put("sdp", sdp.description)
            })
        }
        webSocket.send(message.toString())
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        val message = JSONObject().apply {
            put("iceCandidate", JSONObject().apply {
                put("candidate", candidate.sdp)
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
            })
        }
        webSocket.send(message.toString())
    }

    fun disconnect() {
        webSocket.close(1000, "Closed")
    }
}