package com.example.xiao2.viewmodel;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.xiao2.objects.StatusUpdate;
import com.example.xiao2.repository.DataRepository;
import com.example.xiao2.util.CustomAudioManager;
import com.example.xiao2.util.WebSocketHandler;
import com.nuwarobotics.service.agent.NuwaRobotAPI;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RobotViewModel extends ViewModel {
    private final NuwaRobotAPI mRobotAPI;
    private final Map<String, String> motionMap;
    private final MutableLiveData<Integer> audioFocusState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> audioFocusGranted = new MutableLiveData<>();
    private final MutableLiveData<StatusUpdate> viewModelLiveData = new MutableLiveData<>();
    // 登入狀態 LiveData
    private final MutableLiveData<Boolean> loginStatus = new MutableLiveData<>(false);
    private final DataRepository dataRepository;
    private final CustomAudioManager customAudioManager;
    private final WebSocketHandler webSocketHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String TAG = "RobotViewModel";

    private boolean isCurrentlyPlaying = false;

    public RobotViewModel(NuwaRobotAPI mRobotAPI,
                          DataRepository dataRepository,
                          CustomAudioManager customAudioManager,
                          WebSocketHandler webSocketHandler) {
        this.mRobotAPI = mRobotAPI;
        this.dataRepository = dataRepository;
        this.customAudioManager = customAudioManager;
        this.webSocketHandler = webSocketHandler;

        // 初始化動作映射
        this.motionMap = initializeMotionMap();

        // 監聽狀態變化
        dataRepository.getStatusLiveData().observeForever(this::setStatus);

        // 監聽音頻數據
        dataRepository.getAudioData().observeForever(this::handleAudioData);
    }

    public void setAudioFocusState(int state) {
        audioFocusState.setValue(state);
    }

    public void setAudioFocusGranted(boolean granted) {
        audioFocusGranted.setValue(granted);
    }

    // 新增登入方法
    public void loginToServer(String userId, String userName, String password, boolean newChat, String personality) {
        // 在背景執行緒執行登入
        executor.execute(() -> {
            try {
                // 調用 DataRepository 的登入方法
                boolean result = dataRepository.loginUser(userId, userName, password, newChat, personality);

                // 在主執行緒更新 UI 狀態
                handler.post(() -> loginStatus.setValue(result));
            } catch (Exception e) {
                Log.e(TAG, "Login error: " + e.getMessage(), e);
                handler.post(() -> {
                    // 更新狀態，通知 UI 登入失敗
                    dataRepository.updateStatus(new StatusUpdate("login_failed", "Error: " + e.getMessage()));
                    loginStatus.setValue(false);
                });
            }
        });
    }

    // 處理從服務器接收的音頻
    /**
     * Handle incoming audio data (similar to Python's receive_audio function)
     * @param audioData Raw audio data from the server
     */
    private void handleAudioData(byte[] audioData) {
        if (audioData != null) {
            if (!isCurrentlyPlaying) {
                isCurrentlyPlaying = true;
                // 設置為 speaking 狀態，統一由 handleStatusUpdate 處理錄音控制
                setStatus(new StatusUpdate("speaking"));
            }

            customAudioManager.playAudio(audioData);
            dataRepository.setPlaybackStatus(true);
            Log.d(TAG, "Processing audio: " + audioData.length + " bytes");
        }
    }

    private void startRecording() {
        if (customAudioManager != null && !customAudioManager.isRecording()) {
            customAudioManager.startRecording();
            if (webSocketHandler != null && !webSocketHandler.isStreaming()) {
                webSocketHandler.startStreaming();
            }
            Log.d(TAG, "開始錄音");
        }
    }

    private void stopRecording() {
        if (customAudioManager != null && customAudioManager.isRecording()) {
            customAudioManager.stopRecording();
            Log.d(TAG, "停止錄音");
        }
    }

    // 機器人行為控制
    // 帶結果字符串和情緒的狀態設置
    public void setStatus(@NonNull StatusUpdate statusUpdate) {
        Log.d(TAG, "Status: " + statusUpdate.getStatus() + ", Emotion: " + statusUpdate.getEmotion());

        handleStatusUpdate(statusUpdate);

        // 設置機器人動作
        setRobotMotion(statusUpdate.getStatus());

        // 更新 LiveData
        viewModelLiveData.setValue(statusUpdate);
    }

    private void setRobotMotion(String status) {
        String actualMotion = motionMap.getOrDefault(status, motionMap.getOrDefault("default", ""));
        if (actualMotion != null && !actualMotion.isEmpty()) {
            if (!"thinking".equals(status) || Math.random() > 0.5) {
                mRobotAPI.motionPlay(actualMotion, true);
                Log.d(TAG, "Playing motion: " + actualMotion + " for status: " + status);
            }
        } else {
            Log.w(TAG, "No motion found for status: " + status);
        }
    }

    // 處理狀態更新
    private void handleStatusUpdate(StatusUpdate statusUpdate) {
        if (statusUpdate != null) {
            String status = statusUpdate.getStatus();
            String transcript = statusUpdate.getTranscript();

            // 其他狀態處理
            switch (status) {
                case "listening":
                    startRecording();
                    break;
                case "speaking":
                    stopRecording();
                    break;
                case "thinking":
                    stopRecording();
                    if (transcript != null) {
                        Log.d(TAG, "處理用戶訊息: " + transcript);
                        if (webSocketHandler != null) {
                            webSocketHandler.sendMessage(transcript);
                        }
                    }
                    break;

                case "recognized":
                    if (transcript != null) {
                        Log.d(TAG, "語音識別結果: " + transcript);
                    }
                    break;

                case "reset":
                    interruptAndReset();
                    setStatus(new StatusUpdate("listening"));
                    return;

                case "connecting":
                    Log.d(TAG, "正在連接到WebSocket服務器...");
                    break;

                case "connected":
                    Log.d(TAG, "已成功連接到WebSocket服務器");
                    break;

                case "disconnected":
                    Log.d(TAG, "WebSocket連接已斷開");
                    break;

                case "error":
                    Log.e(TAG, "發生錯誤: " + (transcript != null ? transcript : "未知錯誤"));
                    break;

                case "login_success":
                    Log.d(TAG, "用戶登入成功");
                    break;

                case "login_failed":
                    Log.e(TAG, "用戶登入失敗: " + (transcript != null ? transcript : "未知原因"));
                    break;

                case "takePicture":
                    Log.d(TAG, "拍照請求: " + (transcript != null ? transcript : ""));
                    setStatus(new StatusUpdate("listening", transcript));
                    break;

                default:
                    Log.d(TAG, "未處理的狀態: " + status);
                    break;
            }
        }
    }

    @NonNull
    private Map<String, String> initializeMotionMap() {
        Map<String, String> map = new HashMap<>();

        // 各種狀態對應的機器人動作
        map.put("idling", "666_SA_Discover");
        map.put("thinking", "666_PE_PushGlasses");
        map.put("listening", "666_SA_Think");
        map.put("speaking", "666_RE_Ask");
        map.put("takePicture", "666_SA_Think");
        map.put("error", "666_NE_Cry");
        map.put("reset", "666_SA_Discover");

        // 情緒動作
        map.put("neutral", "666_RE_Ask");
        map.put("angry", "666_NE_Angry");
        map.put("joy", "666_PE_Happy");
        map.put("sad", "666_NE_Cry");
        map.put("surprise", "666_PE_Surprise");
        map.put("scared", "666_NE_Afraid");
        map.put("disgusted", "666_NE_Dizzy");

        return Collections.unmodifiableMap(map);
    }
    
    public LiveData<StatusUpdate> getStatusLiveData() {
        return dataRepository.getStatusLiveData();
    }

    public LiveData<StatusUpdate> getViewModelLiveData(){return viewModelLiveData;}

    public LiveData<Boolean> getSwitchToUserFragment() {
        return dataRepository.getSwitchToUserFragment();
    }

    // 初始化用戶數據並建立連接
    public void setInitialData(String userName, String userId, String personality, String channel) {

        // 保存用戶數據到 DataRepository
        dataRepository.setUserInfo(userName, userId, personality, channel);

        // 連接到WebSocket服務器
        if (webSocketHandler != null) {
            if (!webSocketHandler.isConnected()) {
                webSocketHandler.connect();
            }

            // 發送用戶信息到服務器
            webSocketHandler.sendUserInfo(userName, userId, personality, channel);
        }

        // 設置初始狀態為傾聽
        setStatus(new StatusUpdate("listening"));
    }

    // 中斷並重置機器人
    public void interruptAndReset() {
        Log.d(TAG, "interruptAndReset: Starting to interrupt robot actions.");

        if (mRobotAPI != null) {
            Log.d(TAG, "interruptAndReset: Stopping TTS and listening.");

            // 停止所有動作
            mRobotAPI.motionStop(true);
            Log.d(TAG, "interruptAndReset: Stopped all robot motions.");

            // 重置動作
            mRobotAPI.motionReset();
            Log.d(TAG, "interruptAndReset: Robot motions reset.");

            // 停止音頻播放
            if (customAudioManager != null) {
                customAudioManager.stopPlayback();
                Log.d(TAG, "interruptAndReset: Stopped audio playback.");
            }

            // 確保所有操作已完全停止，添加短暫延遲來等待停止命令生效
            new Handler().postDelayed(() -> Log.d(TAG, "interruptAndReset: Final check after delay to ensure complete reset."), 1500);  // 延遲 1500 毫秒確保機器人完全停止
        } else {
            Log.e(TAG, "interruptAndReset: mRobotAPI is null, cannot stop actions.");
        }
    }

    public void startContinuousStreaming() {
        // Set continuous mode
        if (customAudioManager != null) {
            customAudioManager.setContinuousMode(true);
            customAudioManager.startRecording();
            Log.d(TAG, "CustomAudioManager: Continuous mode enabled, recording started");
        }

        // Notify WebSocket to start stream
        if (webSocketHandler != null) {
            webSocketHandler.startStreaming();
            Log.d(TAG, "WebSocketHandler: Streaming started");
        }

        Log.d(TAG, "Continuous audio streaming started");
    }

    /**
     * Release resources when ViewModel is cleared
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();

        // Stop recording and playback
        if (customAudioManager != null) {
            customAudioManager.stopRecording();
            customAudioManager.stopPlayback();
            customAudioManager.release();
        }

        // Clean up WebSocket
        if (webSocketHandler != null) {
            webSocketHandler.disconnect();
            webSocketHandler.cleanup();
        }

        Log.d(TAG, "RobotViewModel cleared, all resources released");
    }
}