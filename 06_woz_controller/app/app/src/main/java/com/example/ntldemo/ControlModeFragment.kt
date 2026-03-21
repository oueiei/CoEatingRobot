package com.example.ntldemo

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class ControlModeFragment : Fragment() {

    private lateinit var tvRoleName: TextView

    // Video emotion components
    private lateinit var videoView: VideoView
    private var shouldLoopVideo = false
    private var assignedRole: String = ""
    private var emotionVideoMap: Map<String, String> = emptyMap()

    companion object {
        private const val TAG = "ControlModeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_control_mode, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupVideoView()
        updateFromActivity()
    }

    private fun initViews(view: View) {
        videoView = view.findViewById(R.id.video_view)
    }

    private fun setupVideoView() {
        videoView.setOnCompletionListener {
            if (shouldLoopVideo) {
                videoView.start()
            }
        }
    }

    private fun updateFromActivity() {
        val mainActivity = activity as? MainActivity
        mainActivity?.let { activity ->
            assignedRole = activity.assignedRole
            emotionVideoMap = activity.getEmotionVideoMap()
            playDefaultEmotion()
        }
    }

    private fun playDefaultEmotion() {
        playEmotion("idling")
    }

    fun playEmotion(emotion: String) {
        val videoPath = emotionVideoMap[emotion]
        if (videoPath != null) {
            try {
                val videoUri = Uri.parse(videoPath)
                videoView.setVideoURI(videoUri)
                videoView.start()
                Log.d(TAG, "Playing emotion: $emotion")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing emotion video: $emotion", e)
            }
        } else {
            Log.w(TAG, "No video found for emotion: $emotion")
        }
    }

    fun playEmotionByAction(actionValue: String) {
        playEmotion(actionValue)
    }

    fun startTTSEmotion() {
        shouldLoopVideo = true
        playEmotion("neutral")
    }

    fun stopTTSEmotion() {
        shouldLoopVideo = false
        playDefaultEmotion()
    }

    fun updateRoleName(role: String) {
        assignedRole = role
        tvRoleName.text = "角色: $role"
        playDefaultEmotion()
    }

    override fun onPause() {
        super.onPause()
        if (::videoView.isInitialized && videoView.isPlaying) {
            videoView.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::videoView.isInitialized) {
            videoView.stopPlayback()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::videoView.isInitialized) {
            videoView.stopPlayback()
        }
    }
}