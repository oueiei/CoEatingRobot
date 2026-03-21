package com.example.videostreaming.managers

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*

// ==================== Camera Manager ====================
class CameraManager(
    private val context: android.content.Context,
    private val localView: SurfaceViewRenderer
) {
    private var videoCapturer: CameraVideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private val rootEglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory

    private var frontCamera = true
    var isCameraOn = false
        private set

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        localView.init(rootEglBase.eglBaseContext, null)
    }

    fun startCamera() {
        if (isCameraOn) return

        videoCapturer = createCameraCapturer()
        videoSource = peerConnectionFactory.createVideoSource(false)

        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name, rootEglBase.eglBaseContext
        )

        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
        videoCapturer?.startCapture(640, 480, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
        localVideoTrack?.addSink(localView)
        isCameraOn = true
    }

    fun stopCamera() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        videoSource?.dispose()
        videoSource = null
        isCameraOn = false
    }

    fun toggleCamera() {
        if (isCameraOn) stopCamera() else startCamera()
    }

    fun switchCamera() {
        frontCamera = !frontCamera
        if (isCameraOn) {
            stopCamera()
            startCamera()
        }
    }

    fun getVideoTrack(): VideoTrack? = localVideoTrack

    fun getPeerConnectionFactory(): PeerConnectionFactory = peerConnectionFactory

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val camera2Enumerator = Camera2Enumerator(context)
        return camera2Enumerator.deviceNames.firstNotNullOfOrNull { deviceName ->
            when {
                frontCamera && camera2Enumerator.isFrontFacing(deviceName) ->
                    camera2Enumerator.createCapturer(deviceName, null)
                !frontCamera && camera2Enumerator.isBackFacing(deviceName) ->
                    camera2Enumerator.createCapturer(deviceName, null)
                else -> null
            }
        }
    }

    fun release() {
        stopCamera()
        peerConnectionFactory.dispose()
        rootEglBase.release()
    }
}