package com.example.xiao2.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.example.xiao2.objects.StatusUpdate;
import com.example.xiao2.repository.DataRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages audio recording and playback for real-time communication.
 * Optimized for duplex audio transmission with WebSocket.
 */
public class CustomAudioManager {
    private static final String TAG = "CustomAudioManager";

    // 音頻配置
    private static final int SAMPLE_RATE = 24000;
    private static final int MIN_BUFFER_SIZE = 2048;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = Math.max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT),
            AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT));
    private static final int READ_CHUNK_SIZE = 1024 * 2;

    // 依賴項
    private final Context context;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final DataRepository dataRepository;

    // 音頻組件
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private final Queue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();

    // 狀態管理
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private boolean isPlaybackThreadRunning = false;

    // 事件接口
    public interface CustomAudioEventListener {
        void onAudioDataRecorded(byte[] data);
        void onSpeechEnded();
    }
    private CustomAudioEventListener customAudioEventListener;

    // 構造函數
    public CustomAudioManager(Context context, ExecutorService executorService, DataRepository dataRepository) {
        this.context = context;
        this.executorService = executorService;
        this.dataRepository = dataRepository;
        this.mainHandler = new Handler(Looper.getMainLooper());
        initializeAudio();
    }

    public boolean isRecording() {
        return isRecording.get();
    }

    public void setAudioEventListener(CustomAudioEventListener listener) {
        this.customAudioEventListener = listener;
    }

    // 初始化音頻組件
    private void initializeAudio() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Recording permission not granted");
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_IN,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    BUFFER_SIZE,
                    AudioTrack.MODE_STREAM
            );

            Log.d(TAG, "Audio components initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing audio components", e);
        }
    }

    // 低延遲播放任務
    // 播放任務 - 只負責播放音頻
    private void lowLatencyPlaybackTask() {
        try {
            // 確保 AudioTrack 已初始化
            if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                configureAudioTrack();
            }

            // 開始播放
            audioTrack.play();

            // 處理隊列中的音頻數據
            int silenceCounter = 0;
            while (isPlaying.get()) {
                byte[] chunk = audioQueue.poll();

                if (chunk != null) {
                    silenceCounter = 0;
                    int result = audioTrack.write(chunk, 0, chunk.length, AudioTrack.WRITE_BLOCKING);
                    if (result < 0) {
                        Log.e(TAG, "Error writing to AudioTrack: " + result);
                    }
                } else {
                    silenceCounter++;
                    if (silenceCounter > 50) { // 500ms 無數據
                        Log.d(TAG, "No audio data for 500ms, ending playback");
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            // 結束播放
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.stop();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in low latency playback", e);
        } finally {
            isPlaybackThreadRunning = false;
            isPlaying.set(false);
            audioQueue.clear();

            // 在播放任務完成後，調用播放完成處理
            mainHandler.post(this::handlePlaybackCompletion);
        }
    }

    // 新方法：處理播放完成
    private void handlePlaybackCompletion() {
        Log.d(TAG, "Audio playback completed, handling completion");

        // 確保有足夠的延遲以完成播放
        mainHandler.postDelayed(() -> {
            if (dataRepository != null) {
                // 更新播放狀態
                dataRepository.setPlaybackStatus(false);
                // 通知播放完成
                dataRepository.updateStatus(new StatusUpdate("listening"));
                Log.d(TAG, "Playback complete notification sent");
            }
        }, 200); // 200ms 延遲確保音頻完全播放完畢
    }

    // 配置 AudioTrack
    private void configureAudioTrack() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_OUT)
                            .build())
                    .setBufferSizeInBytes(MIN_BUFFER_SIZE)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } else {
            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    Math.max(MIN_BUFFER_SIZE, AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)),
                    AudioTrack.MODE_STREAM
            );
        }
    }

    // 停止播放
    public void stopPlayback() {
        if (isPlaying.get()) {
            isPlaying.set(false);
            if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    audioTrack.pause();
                    audioTrack.flush();
                    audioTrack.stop();
                    Log.d(TAG, "Audio playback stopped");
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping audio playback", e);
                }
            }
        }
    }

    // 開始錄音
    public void startRecording() {
        if (!isRecording.get()) {
            isRecording.set(true);
            executorService.execute(this::recordingTask);
            Log.d(TAG, "Recording started");
        } else {
            Log.d(TAG, "Already recording");
        }
    }

    // 停止錄音
    public void stopRecording() {
        if (isRecording.get()) {
            isRecording.set(false);

            // 立即停止 AudioRecord
            if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    audioRecord.stop();
                    Log.d(TAG, "AudioRecord stopped immediately");
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping AudioRecord", e);
                }
            }

            Log.d(TAG, "Recording stopped");

            if (dataRepository != null) {
                dataRepository.updateStatus(new StatusUpdate("stop_streaming"));
            }
        }
    }

    // 錄音任務
    private void recordingTask() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Recording permission not granted");
            isRecording.set(false);
            return;
        }

        try {
            audioRecord.startRecording();
            byte[] buffer = new byte[READ_CHUNK_SIZE];

            while (isRecording.get()) {
                if (isPaused.get()) {
                    Thread.sleep(100);
                    continue;
                }

                int bytesRead = audioRecord.read(buffer, 0, READ_CHUNK_SIZE);
                if (bytesRead > 0) {
                    // 發送音頻數據
                    if (dataRepository != null) {
                        byte[] dataCopy = new byte[bytesRead];
                        System.arraycopy(buffer, 0, dataCopy, 0, bytesRead);
                        dataRepository.sendAudioData(dataCopy);
                    }

                    // 通知監聽器
                    if (customAudioEventListener != null) {
                        byte[] dataCopy = new byte[bytesRead];
                        System.arraycopy(buffer, 0, dataCopy, 0, bytesRead);
                        mainHandler.post(() -> customAudioEventListener.onAudioDataRecorded(dataCopy));
                    }
                }

                Thread.sleep(10); // 防止 CPU 過載
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in recording task", e);
        } finally {
            if (audioRecord != null) {
                audioRecord.stop();
            }
        }
    }

    // 播放音頻
    public void playAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return;
        }

        // 如果正在錄音，停止錄音
        if (isRecording.get()) {
            stopRecording();
            Log.d(TAG, "停止錄音以防止回饋");
        }

        // 添加音頻數據到播放隊列
        audioQueue.offer(audioData);
        Log.d(TAG, "Added audio data: " + audioData.length + " bytes, queue size: " + audioQueue.size());

        // 如果播放線程未運行，啟動它
        if (!isPlaybackThreadRunning) {
            isPlaybackThreadRunning = true;
            isPlaying.set(true);

            if (dataRepository != null) {
                dataRepository.setPlaybackStatus(true);
            }

            executorService.execute(this::lowLatencyPlaybackTask);
        }
    }

    // 釋放資源
    public void release() {
        stopRecording();
        stopPlayback();

        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }

        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }

        Log.d(TAG, "Audio resources released");
    }

    // 設置連續模式 (簡化)
    public void setContinuousMode(boolean continuous) {
        if (continuous) {
            // 連續模式下的額外設置
            isPaused.set(false);
        }
    }
}