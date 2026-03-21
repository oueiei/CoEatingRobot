package com.example.xiao2.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.annotation.NonNull;

import com.example.xiao2.objects.StatusUpdate;
import com.example.xiao2.util.SocketHandlerInterface;

import java.util.concurrent.ExecutorService;

/**
 * Repository layer in MVVM pattern.
 * Manages data operations between ViewModel and data sources (WebSocket, local storage).
 * Single source of truth for the application data.
 */
public class DataRepository {
    private static final String TAG = "DataRepository";
    
    // LiveData objects for reactive UI updates
    private final MutableLiveData<StatusUpdate> statusLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> switchToUserFragment = new MutableLiveData<>();
    private final MutableLiveData<byte[]> audioDataLiveData = new MutableLiveData<>();
    private final MutableLiveData<byte[]> receivedAudio = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlayingAudio = new MutableLiveData<>(false);

    private final SocketHandlerInterface socketHandler;
    @NonNull
    private final ExecutorService executorService;

    /**
     * Constructor with dependency injection
     * @param socketHandler The WebSocket handler interface
     * @param executorService Thread pool for background operations
     */
    public DataRepository(@NonNull SocketHandlerInterface socketHandler, 
                          @NonNull ExecutorService executorService) {
        // Dependencies
        this.socketHandler = socketHandler;
        this.executorService = executorService;

        // Observe incoming audio from WebSocket
        socketHandler.getIncomingAudio().observeForever(audioData -> {
            if (audioData != null) {
                receivedAudio.postValue(audioData);
            }
        });
        
        // React to status changes
        statusLiveData.observeForever(statusUpdate -> {
            if (statusUpdate != null) {
                String status = statusUpdate.getStatus();
                String Transcript = statusUpdate.getTranscript();

                if ("streaming".equals(status) && "start".equals(Transcript)) {
                    // Only start streaming when explicitly requested
                    socketHandler.startStreaming();
                } else if ("streaming".equals(status) && "stop".equals(Transcript)) {
                    socketHandler.stopStreaming();
                }
            }
        });
    }
    
    /**
     * Set user information for the session
     * @param userName User's name
     * @param userId User's ID
     * @param personality User's personality type
     * @param channel Communication channel
     */
    public void setUserInfo(String userName, String userId, String personality, String channel) {
        // User information

        // Send user info to server
//        socketHandler.sendUserInfo(userName, userId, personality, channel);
        Log.d(TAG, "User info set and sent to server");
    }


    /**
     * Process incoming audio data from the server
     * @param audioData Raw audio data as byte array
     */
    public void handleIncomingAudio(byte[] audioData) {
        audioDataLiveData.postValue(audioData);
    }

    /**
     * Get LiveData for incoming audio to observe in ViewModel
     * @return LiveData stream of audio data
     */
    public LiveData<byte[]> getAudioData() {
        return audioDataLiveData;
    }

    /**
     * Send audio data to the server
     * @param audioData Raw audio data as byte array
     */
    public void sendAudioData(byte[] audioData) {
        if (audioData != null) {
            Log.d(TAG, "Sending audio data: " + audioData.length + " bytes");
            // Forward to WebSocket
            socketHandler.sendAudioData(audioData);
        }
    }

    public boolean loginUser(String userId, String userName, String password, boolean newChat, String personality) {
        if (socketHandler != null) {
            boolean loginResult = socketHandler.login(userId, userName, password, newChat, personality);

            if (loginResult) {
                // 登入成功後更新狀態
                updateStatus(new StatusUpdate("login_success"));

                // 嘗試建立 WebSocket 連接
                socketHandler.connect();
            } else {
                updateStatus(new StatusUpdate("login_failed"));
            }

            return loginResult;
        }

        return false;
    }

    /**
     * Update status with StatusUpdate Object
     * @param status Status code
     */
    public void updateStatus(StatusUpdate status) {
        Log.d(TAG, "Status updated to: " + status + 
              (status.getTranscript() != null ? " with message: " + status.getTranscript() : ""));
        statusLiveData.postValue(status);
    }

    /**
     * Get LiveData for status updates
     * @return LiveData of StatusUpdate
     */
    public LiveData<StatusUpdate> getStatusLiveData() {
        return statusLiveData;
    }

    /**
     * Get LiveData for fragment navigation
     * @return LiveData for UI navigation
     */
    public LiveData<Boolean> getSwitchToUserFragment() {
        return switchToUserFragment;
    }

    /**
     * Request navigation to user fragment
     */
    public void requestSwitchToUserFragment() {
        switchToUserFragment.postValue(true);
    }

    public void setPlaybackStatus(boolean isPlaying) {
        isPlayingAudio.postValue(isPlaying);
    }
    
    /**
     * Get audio playback status
     * @return LiveData containing playback status
     */
    public LiveData<Boolean> getPlaybackStatus() {
        return isPlayingAudio;
    }

}