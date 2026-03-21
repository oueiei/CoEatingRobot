package com.example.xiao2.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.VideoView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.xiao2.R;
import com.example.xiao2.objects.StatusUpdate;
import com.example.xiao2.viewmodel.RobotViewModel;
import com.example.xiao2.viewmodel.SharedViewModel;

import java.util.Map;

public class VideoFragment extends Fragment {
    private static final String TAG = "VideoFragment";
    private RobotViewModel robotViewModel;
    private SharedViewModel sharedViewModel;
    private String personalityType;
    private VideoView videoView;
    private TextView subtitleText;
    private Map<String, String> emotionVideoMap;
    private boolean shouldLoopVideo = false;
    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        robotViewModel = new ViewModelProvider(requireActivity()).get(RobotViewModel.class);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        sharedViewModel.getEmotionVideoMap().observe(this, map -> this.emotionVideoMap = map);

        // 從 SharedPreferences 或其他方式獲取當前用户的MBTI性格
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("user_data", Context.MODE_PRIVATE);
        String mbti = sharedPreferences.getString("selected_personality", "ENFP");

        // 決定使用內向還是外向的表情視頻
        personalityType = mbti.startsWith("E") ? "e" : "i";

        // 初始化 AudioManager
        audioManager = (AudioManager) requireActivity().getSystemService(Context.AUDIO_SERVICE);

        // 創建音頻焦點監聽器
        audioFocusChangeListener = focusChange -> {
            Log.d(TAG, "Audio focus changed: " + focusChange);
            // 通知 ViewModel 音頻焦點狀態變化
            robotViewModel.setAudioFocusState(focusChange);

            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    // 獲得音頻焦點後的處理
                    Log.d(TAG, "Audio focus gained");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    // 完全失去音頻焦點
                    Log.d(TAG, "Audio focus lost");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // 暫時失去音頻焦點
                    Log.d(TAG, "Audio focus lost temporarily");
                    break;
            }
        };
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video, container, false);
        videoView = view.findViewById(R.id.video_view);
        subtitleText = view.findViewById(R.id.subtitle_text);

        setupVideoCompletionListener();
        return view;
    }

    // In VideoFragment.java - onViewCreated method
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // This is the ONLY line you need to start streaming
//        robotViewModel.startContinuousStreaming();
    }

    @Override
    public void onStart() {
        super.onStart();

        // Request audio focus
        requestAudioFocus();

        // Observe status updates with proper lifecycle awareness
        robotViewModel.getViewModelLiveData().observe(getViewLifecycleOwner(), this::updateVideoState);

        // Start continuous streaming
        robotViewModel.startContinuousStreaming();
    }
    // 請求音頻焦點的方法
    private void requestAudioFocus() {
        int result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
        );

        boolean isGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        Log.d(TAG, "Audio focus request result: " + (isGranted ? "granted" : "denied"));

        // 通知 ViewModel 音頻焦點是否已授予
        robotViewModel.setAudioFocusGranted(isGranted);
    }
    // 在 VideoFragment.java 中
    private void updateVideoState(StatusUpdate statusUpdate) {
        Log.d(TAG, "updateVideoState called with: " + (statusUpdate != null ? statusUpdate.getStatus() : "null"));

        if (statusUpdate != null) {
            String status = statusUpdate.getStatus();
            String emotion = statusUpdate.getEmotion();
            String transcript = statusUpdate.getTranscript();
            Log.d(TAG, "Status Update: " + status + ", Emotion: " + emotion + ", Transcript:" + transcript);

            switch (status) {
                case "recognized":
                    // 更新字幕
                    subtitleText.setText(statusUpdate.getTranscript());
                    // 不要改變當前視頻播放狀態
                    break;

                case "speaking":
                    // 播放狀態下，視頻應循環播放
                    shouldLoopVideo = true;
                    playExpressionVideo(transcript);
                    break;

                case "listening":
                    // 只有在非說話狀態才切換到聆聽
                    shouldLoopVideo = false;
                    playExpressionVideo("listening");
                    break;

                case "idling":
                    // 閒置狀態
                    shouldLoopVideo = false;
                    playExpressionVideo("idling");
                    break;

                default:
                    Log.d(TAG, "Unhandled status: " + status);
                    break;
            }
        }
    }

    private void playExpressionVideo(String baseEmotion) {
        if (baseEmotion == null || baseEmotion.isEmpty()) {
            baseEmotion = "neutral";
        }

        // 格式化完整的表情鍵值
        String fullEmotionKey = personalityType + "_" + baseEmotion;
        Log.d(TAG, "Video status update to:" + fullEmotionKey);

        String videoPath = emotionVideoMap.get(fullEmotionKey);
        if (videoPath != null) {
            Uri videoUri = Uri.parse(videoPath);
            videoView.setVideoURI(videoUri);

            // 設置 OnPrepared 監聽器來處理循環邏輯
            videoView.setOnPreparedListener(mp -> {
                // 說話狀態下的影片應該循環播放直到收到新狀態
                if (shouldLoopVideo) {
                    mp.setLooping(true);
                    Log.d(TAG, "Set video to loop mode");
                } else {
                    mp.setLooping(false);
                }
            });

            videoView.start();
            Log.d(TAG, "Playing video for emotion key: " + fullEmotionKey +
                    " (path: " + videoPath + ") with loop: " + shouldLoopVideo);
        } else {
            Log.e(TAG, "No video path found for emotion key: " + fullEmotionKey);

            if (!baseEmotion.equals("neutral")) {
                Log.d(TAG, "Trying fallback to neutral emotion");
                playExpressionVideo("idling");
            }
        }
    }

    private void setupVideoCompletionListener() {
        videoView.setOnCompletionListener(mp -> {
            Log.d(TAG, "Video playback completed, shouldLoop: " + shouldLoopVideo);
            if (shouldLoopVideo) {
                videoView.start();
                Log.d(TAG, "Restarting video for loop playback");
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "VideoFragment onPause called");
        // 暫停視頻播放
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "VideoFragment onStop called");
        // 停止視頻播放
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "VideoFragment onDestroyView called - performing cleanup");

        // Stop video playback
        if (videoView != null) {
            videoView.stopPlayback();
            videoView = null; // Allow for garbage collection
        }

        // Remove observers to prevent memory leaks
        if (robotViewModel != null) {
            // Remove any observers that might prevent garbage collection
            robotViewModel.getStatusLiveData().removeObservers(getViewLifecycleOwner());
        }

        if (sharedViewModel != null) {
            sharedViewModel.getEmotionVideoMap().removeObservers(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "VideoFragment onDestroy called - finalizing cleanup");

        // Ensure robot is fully reset
        if (robotViewModel != null) {
            robotViewModel.interruptAndReset();
        }

        // Force garbage collection
        System.gc();
    }

}