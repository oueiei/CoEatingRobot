package com.example.xiao2.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
//import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.example.xiao2.R;
import com.example.xiao2.objects.StatusUpdate;
import com.example.xiao2.viewmodel.RobotViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class LoginFragment extends Fragment {
    private static final String TAG = "LoginFragment";

    private EditText usernameEditText, passwordEditText;
//    private Spinner personalitySpinner;
    private ProgressBar progressBar;
    private RobotViewModel robotViewModel;

    // SharedPreferences 用於存儲用戶資料
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "user_data";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USER_ID = "user_id"; // 用於存儲使用者 ID
    private static final String KEY_CREATED_DATE = "created_date"; // 用於存儲使用者 ID
    private static final String KEY_PERSONALITY = "personality"; // 用於存儲性格類型

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        robotViewModel = new ViewModelProvider(requireActivity()).get(RobotViewModel.class);

        // 監聽連接狀態
        robotViewModel.getStatusLiveData().observe(this, this::handleLoginStatus);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        usernameEditText = view.findViewById(R.id.usernameEditText);
        passwordEditText = view.findViewById(R.id.passwordEditText);
        // 假設您已經在佈局中添加了這些元素
//        personalitySpinner = view.findViewById(R.id.personalitySpinner);
        progressBar = view.findViewById(R.id.progressBar);

        Button loginButton = view.findViewById(R.id.loginButton);
        Button registerButton = view.findViewById(R.id.registerButton);

        // 初始化 SharedPreferences
        sharedPreferences = Objects.requireNonNull(requireActivity()).getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 設置登入按鈕點擊事件
        loginButton.setOnClickListener(v -> attemptLogin());

        // 設置註冊按鈕點擊事件
        registerButton.setOnClickListener(v -> registerUser());

        // 設置點擊監聽器來隱藏鍵盤
        view.setOnTouchListener(this::onTouch);

        return view;
    }

    // 隱藏鍵盤的輔助方法
    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // 驗證使用者是否存在並處理登入邏輯
    private void attemptLogin() {
        String username = usernameEditText.getText().toString();
        String password = passwordEditText.getText().toString();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(getActivity(), "請輸入使用者名稱和密碼", Toast.LENGTH_SHORT).show();
            return;
        }

        // 驗證使用者是否存在
        String registeredUsername = sharedPreferences.getString(KEY_USERNAME, null);
        String registeredPassword = sharedPreferences.getString(KEY_PASSWORD, null);
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String personality = sharedPreferences.getString(KEY_PERSONALITY, "ENFP");

        if (username.equals(registeredUsername) && password.equals(registeredPassword)) {
            // 本地驗證通過後，嘗試通過 TCP 連接登入服務器

            // 顯示進度條
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }

            // 調用 ViewModel 中的登入方法
            robotViewModel.loginToServer(userId, username, password, true, personality);

            Log.d(TAG, "正在嘗試登入服務器，使用者 ID：" + userId);
        } else {
            Toast.makeText(getActivity(), "使用者名稱或密碼錯誤", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "本地驗證失敗");
        }
    }

    // 註冊新使用者
    private void registerUser() {
        String username = usernameEditText.getText().toString();
        String password = passwordEditText.getText().toString();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(getActivity(), "請輸入使用者名稱和密碼進行註冊", Toast.LENGTH_SHORT).show();
            return;
        }

        // 為新使用者創建唯一 ID
        String userId = UUID.randomUUID().toString();
        // 創建日期格式
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        // 獲取當前日期時間
        String createdDate = sdf.format(new Date());
        // 獲取所選性格類型
        String personality = "ENFP";

        // 儲存使用者資料到 SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_PASSWORD, password);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_CREATED_DATE, createdDate);
        editor.putString(KEY_PERSONALITY, personality);
        editor.apply();

        // 嘗試使用新註冊的資訊登入服務器
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        // 調用 ViewModel 中的登入方法
        robotViewModel.loginToServer(userId, username, password, true, personality);

        Toast.makeText(getActivity(), "註冊成功，正在連接服務器...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "使用者註冊成功，使用者 ID：" + userId + "，正在嘗試登入服務器");
    }

    // 跳轉到 UserFragment
    private void proceedToNextScreen() {
        View loginLayout = requireActivity().findViewById(R.id.login_layout);
        if (loginLayout != null) {
            loginLayout.setVisibility(View.GONE);
        }

        // 隱藏進度條
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        // 使用 FragmentTransaction 進行跳轉
        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new UserFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void handleLoginStatus(StatusUpdate statusUpdate) {
        if (statusUpdate != null) {
            String status = statusUpdate.getStatus();
            Log.d(TAG, "收到狀態更新: " + status);

            switch (status) {
                case "login_success":
                    // 登入成功後進行跳轉
                    Toast.makeText(requireContext(), "登入成功", Toast.LENGTH_SHORT).show();
                    proceedToNextScreen();
                    break;

                case "login_failed":
                    // 隱藏進度條
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Toast.makeText(requireContext(), "登入失敗: " +
                            statusUpdate.getTranscript(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "登入失敗: " + statusUpdate.getTranscript());
                    break;

                case "connecting":
                    Toast.makeText(requireContext(), "正在連接服務器...", Toast.LENGTH_SHORT).show();
                    break;

                case "connected":
                    // WebSocket 連接成功
                    Toast.makeText(requireContext(), "連接成功", Toast.LENGTH_SHORT).show();
                    // 如果還沒有跳轉，現在可以跳轉
                    proceedToNextScreen();
                    break;

                case "disconnected":
                    // 隱藏進度條
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Toast.makeText(requireContext(), "連接中斷: " +
                            statusUpdate.getTranscript(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "連接中斷: " + statusUpdate.getTranscript());
                    break;

                case "error":
                    // 隱藏進度條
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Toast.makeText(requireContext(), "連接錯誤: " +
                            statusUpdate.getTranscript(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "連接錯誤: " + statusUpdate.getTranscript());
                    break;
            }
        }
    }

    private boolean onTouch(View v, MotionEvent event) {
        hideKeyboard();
        return false;
    }
}