package com.example.xiao2;


import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.FragmentTransaction;

import com.example.xiao2.fragment.UserFragment;
import com.example.xiao2.fragment.VideoFragment;
import com.example.xiao2.listeners.CustomRobotEventListener;
import com.example.xiao2.objects.StatusUpdate;
import com.example.xiao2.repository.DataRepository;
import com.example.xiao2.util.CustomAudioManager;
import com.example.xiao2.util.RobotEventCallback;
import com.example.xiao2.util.WebSocketHandler;
import com.example.xiao2.viewmodel.RobotViewModel;
import com.example.xiao2.viewmodel.RobotViewModelFactory;
import com.example.xiao2.fragment.LoginFragment;
import com.example.xiao2.viewmodel.SharedViewModel;
import com.example.xiao2.viewmodel.SharedViewModelFactory;
import com.nuwarobotics.service.IClientId;
import com.nuwarobotics.service.agent.NuwaRobotAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements RobotEventCallback {

    // SharedPreferences 文件名和鍵
    private static final String PREF_NAME = "user_data";
    private static final String KEY_IS_INITIALIZED = "is_initialized";
    private TextView tapToStartTextView;
    private FragmentContainerView fragmentContainer;

    private Handler mainHandler;
    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;
    private NuwaRobotAPI mRobotAPI;
    private CustomAudioManager customAudioManager;
    private RobotViewModel robotViewModel;
    private WebSocketHandler webSocketHandler;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 設置對應佈局

        // 初始化 TextView 和 Layout
        tapToStartTextView = findViewById(R.id.tap_to_start);
        RelativeLayout mainLayout = findViewById(R.id.main_layout);
        fragmentContainer = findViewById(R.id.fragment_container);

        // 確保 tapToStartTextView 不為 null
        if (tapToStartTextView != null) {
            Log.d("MainActivity", "tapToStartTextView initialized successfully.");
        } else {
            Log.e("MainActivity", "tapToStartTextView is null.");
        }

        // 呼吸閃爍效果
        startBreathingEffect();

        mainLayout.setOnClickListener(v -> {
            Log.d("MainActivity", "Screen tapped.");

            // 清除動畫並隱藏 "輕按以開始" 的文本
            if (tapToStartTextView != null) {
                Log.d("MainActivity", "Hiding tapToStartTextView");
                tapToStartTextView.clearAnimation();  // 清除動畫
                tapToStartTextView.setVisibility(View.GONE);
                tapToStartTextView.requestLayout();  // 強制重新佈局
            }

            // 顯示 Fragment 容器並加載 LoginFragment
            fragmentContainer.setVisibility(View.VISIBLE);

            // 使用 FragmentTransaction 轉場
            switchFragment(LoginFragment.class);
        });

        // 初始化 Handler 以在主線程處理任務
        mainHandler = new Handler(Looper.getMainLooper());


        // 檢查權限並初始化
        checkPermissions();

        // 初始化 SharedPreferences
        initializeSharedPreferences();

        // 觀察 ViewModel 中的 Fragment 切換請求
        robotViewModel.getSwitchToUserFragment().observe(this, switchToUserFragment -> {
            if (switchToUserFragment != null && switchToUserFragment) {
                switchFragment(UserFragment.class);
            }
        });

        robotViewModel.getStatusLiveData().observe(this, status -> {
            if (status != null) {
                handleGlobalStatus(status);
            }
        });

    }

    private void handleGlobalStatus(StatusUpdate statusUpdate) {
        String status = statusUpdate.getStatus();

        switch (status) {
            case "login_success":
                switchFragment(UserFragment.class);
                break;

            case "start_chat":
                switchFragment(VideoFragment.class);
                break;

            case "session_end":
                switchFragment(UserFragment.class);
                break;

            case "logout":
                switchFragment(LoginFragment.class);
                break;

            case "error":
                // 顯示錯誤訊息，但不切換 Fragment
                Toast.makeText(this, "發生錯誤: " + statusUpdate.getTranscript(),
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }

    /**
     * 切換到指定的 Fragment
     *
     * @param fragmentClass 要切換到的 Fragment 類
     */
    private void switchFragment(Class<? extends Fragment> fragmentClass) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        // 清除所有返回堆疊
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        FragmentTransaction transaction = fragmentManager.beginTransaction();

        try {
            // 創建 Fragment 實例
            Fragment fragment = fragmentClass.newInstance();
            transaction.replace(R.id.fragment_container, fragment);
//            transaction.addToBackStack(null);  // 加入返回棧
            transaction.commit();
        } catch (Exception e) {
            Log.e(TAG, "切換 Fragment 失敗: " + e.getMessage());
        }
    }

    // 在 MainActivity 中初始化影片路徑的邏輯
    ///Users/changhungchun/SWE/jxw/frontend/app/src/main/res/raw
    private void initializeEmotionVideoMap(@NonNull HashMap<String, String> emotionVideoMap) {
        // Introvert
        emotionVideoMap.put("i_idling", "android.resource://" + getPackageName() + "/" + R.raw.i_neutral_n);
        emotionVideoMap.put("i_thinking", "android.resource://" + getPackageName() + "/" + R.raw.i_thinking_n);
        emotionVideoMap.put("i_listening", "android.resource://" + getPackageName() + "/" + R.raw.i_listening_n);
        emotionVideoMap.put("i_error", "android.resource://" + getPackageName() + "/" + R.raw.i_sad_n);
        emotionVideoMap.put("i_reset", "android.resource://" + getPackageName() + "/" + R.raw.i_neutral_n);

        // Introvert: Emotion in Speaking
        emotionVideoMap.put("i_neutral", "android.resource://" + getPackageName() + "/" + R.raw.i_neutral_s);
        emotionVideoMap.put("i_angry", "android.resource://" + getPackageName() + "/" + R.raw.i_angry_s);
        emotionVideoMap.put("i_joy", "android.resource://" + getPackageName() + "/" + R.raw.i_joy_s);
        emotionVideoMap.put("i_sad", "android.resource://" + getPackageName() + "/" + R.raw.i_sad_n);
        emotionVideoMap.put("i_surprise", "android.resource://" + getPackageName() + "/" + R.raw.i_surprise_s);
        emotionVideoMap.put("i_scared", "android.resource://" + getPackageName() + "/" + R.raw.i_scared_s);
        emotionVideoMap.put("i_disgusted", "android.resource://" + getPackageName() + "/" + R.raw.i_disgusted_n);

        // Extrovert: Status
        emotionVideoMap.put("e_idling", "android.resource://" + getPackageName() + "/" + R.raw.e_neutral_n);
        emotionVideoMap.put("e_thinking", "android.resource://" + getPackageName() + "/" + R.raw.e_thinking_n);
        emotionVideoMap.put("e_listening", "android.resource://" + getPackageName() + "/" + R.raw.e_listening_n);
        emotionVideoMap.put("e_error", "android.resource://" + getPackageName() + "/" + R.raw.e_sad_n);
        emotionVideoMap.put("e_reset", "android.resource://" + getPackageName() + "/" + R.raw.e_neutral_n);


        // Extrovert: Emotion in Speaking
        emotionVideoMap.put("e_neutral", "android.resource://" + getPackageName() + "/" + R.raw.e_neutral_s);
        emotionVideoMap.put("e_angry", "android.resource://" + getPackageName() + "/" + R.raw.e_angry_s);
        emotionVideoMap.put("e_joy", "android.resource://" + getPackageName() + "/" + R.raw.e_joy_s);
        emotionVideoMap.put("e_sad", "android.resource://" + getPackageName() + "/" + R.raw.e_sad_n);
        emotionVideoMap.put("e_surprise", "android.resource://" + getPackageName() + "/" + R.raw.e_surprise_s);
        emotionVideoMap.put("e_scared", "android.resource://" + getPackageName() + "/" + R.raw.e_scared_s);
        emotionVideoMap.put("e_disgusted", "android.resource://" + getPackageName() + "/" + R.raw.e_disgusted_n);

    }

    private void startBreathingEffect() {
        // 創建呼吸閃爍的動畫，淡入淡出
        AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f, 0.2f);
        alphaAnimation.setDuration(1200); // 動畫持續時間
        alphaAnimation.setRepeatCount(Animation.INFINITE); // 無限重複
        alphaAnimation.setRepeatMode(Animation.REVERSE); // 反向重複

        // 啟動動畫
        if (tapToStartTextView != null) {
            tapToStartTextView.startAnimation(alphaAnimation);
        } else {
            Log.e("MainActivity", "TextView tap_to_start is null");
        }
    }

    @Override
    public void postToUiThread(Runnable runnable) {
        // 使用 Handler 將任務傳遞到主線程
        if (mainHandler != null) {
            mainHandler.post(runnable);
        }
    }

    // 初始化 SharedPreferences 的方法
    private void initializeSharedPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 檢查是否已經初始化
        boolean isInitialized = sharedPreferences.getBoolean(KEY_IS_INITIALIZED, false);

        if (!isInitialized) {
            // 如果沒有初始化，則執行初始化邏輯
            SharedPreferences.Editor editor = sharedPreferences.edit();

            // 設置初始化標記，避免下次重複初始化
            editor.putBoolean(KEY_IS_INITIALIZED, true);

            // 提交變更
            editor.apply();

            // 打印初始化成功的消息
            Log.i("MainActivity", "SharedPreferences 已初始化，存儲了預設的使用者資訊。");
        } else {
            // 如果已經初始化過，則打印消息
            Log.i("MainActivity", "SharedPreferences 已經初始化，無需重複操作。");
        }
    }

    // 檢查權限
    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE
        };
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), REQUEST_ID_MULTIPLE_PERMISSIONS);
        } else {
            // 如果所有權限都被授予，則進行初始化
            initializeComponents();
        }
    }
    // 初次使用時，會要求使用者允許權限，權限被允許之後，才會開始初始化
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ID_MULTIPLE_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                // 如果所有權限都已授予，重新檢查權限（這將導致初始化）
                checkPermissions();
            } else {
                Toast.makeText(this, "需要授予所有權限才能使用此應用", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // Inside the initializeComponents() method in MainActivity.java
    private void initializeComponents() {
        // 初始化 NuwaRobotAPI
        IClientId mClientId = new IClientId(this.getPackageName());
        mRobotAPI = new NuwaRobotAPI(this, mClientId);

        // 初始化 WebSocket
        webSocketHandler = new WebSocketHandler();
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String serverIp = prefs.getString("server_ip", "140.112.14.225");
        int wsPort = prefs.getInt("ws_port", 8765);
        int loginPort = prefs.getInt("login_port", 12345);
        webSocketHandler.setServerConfig(serverIp, wsPort, loginPort);

        // 初始化 DataRepository 並傳入 WebSocket
        DataRepository dataRepository = new DataRepository(webSocketHandler, executorService);

        // 傳入 dataRepo
        webSocketHandler.setDataRepository(dataRepository);

        // 實現多線程的雙向全工收音，傳入 DataRepository 以便直接發送音頻數據
        customAudioManager = new CustomAudioManager(this, executorService, dataRepository);

        // 設置音頻事件監聽器（可選）
        customAudioManager.setAudioEventListener(new CustomAudioManager.CustomAudioEventListener() {
            @Override
            public void onAudioDataRecorded(byte[] data) {
                // 這裡可以處理錄製到的音頻數據
                Log.d("MainActivity", "Audio data recorded: " + data.length + " bytes");
            }

            @Override
            public void onSpeechEnded() {
                // 這裡可以處理檢測到語音結束的事件
                Log.d("MainActivity", "Speech ended detected");
            }
        });

        // 初始化 CustomRobotEventListener
        CustomRobotEventListener customRobotEventListener = new CustomRobotEventListener(mRobotAPI, dataRepository, this);

        // 註冊 RobotEventListener
        mRobotAPI.registerRobotEventListener(customRobotEventListener);

        // 初始化 CameraX 並選擇鏡頭方向
        initializeCameraX();

        // 初始化 RobotViewModel 並傳遞所有必要組件
        RobotViewModelFactory factory = new RobotViewModelFactory(
                mRobotAPI,
                dataRepository,
                customAudioManager,
                webSocketHandler
        );
        robotViewModel = new ViewModelProvider(this, factory).get(RobotViewModel.class);

        HashMap<String, String> emotionVideoMap = new HashMap<>();
        initializeEmotionVideoMap(emotionVideoMap);  // 將影片路徑加入 HashMap

        SharedViewModelFactory sharedViewModelFactory = new SharedViewModelFactory(emotionVideoMap);
        SharedViewModel sharedViewModel = new ViewModelProvider(this, sharedViewModelFactory).get(SharedViewModel.class);
        sharedViewModel.setEmotionVideoMap(emotionVideoMap);

        // 設置默認初始狀態
        robotViewModel.setStatus(new StatusUpdate("idling"));
    }

    private void initializeCameraX() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // 設置預覽
                Preview preview = new Preview.Builder().build();

                // 選擇相機鏡頭方向（前置鏡頭）
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)  // 選擇前置鏡頭
                        .build();

                // 綁定相機和預覽
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "相機初始化失敗: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 清理 NuwaRobotAPI 資源
        if (mRobotAPI != null) {
            mRobotAPI.release();
        }

        // 關閉 WebSocket 連接
        if (webSocketHandler != null) {
            webSocketHandler.disconnect();
        }

        // 釋放音頻資源
        if (customAudioManager != null) {
            customAudioManager.release();
        }

        // 關閉 ExecutorService 以確保所有線程正確終止
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
