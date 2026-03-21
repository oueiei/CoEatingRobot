package com.example.xiao2.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.xiao2.objects.StatusUpdate;
import com.example.xiao2.repository.DataRepository;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of SocketHandlerInterface to manage WebSocket connections for real-time
 * duplex communication with the server.
 */
public class WebSocketHandler implements SocketHandlerInterface {
    private static final String TAG = "WebSocketHandler";
    private static final String DEFAULT_SERVER_HOST = "140.112.14.225";
    private static final int DEFAULT_WS_PORT = 8765;
    private static final int DEFAULT_LOGIN_PORT = 12345;
    private String serverHost = DEFAULT_SERVER_HOST;
    private int wsPort = DEFAULT_WS_PORT;
    private int loginPort = DEFAULT_LOGIN_PORT;
    private String wsUrl = "ws://" + DEFAULT_SERVER_HOST + ":" + DEFAULT_WS_PORT;
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    private WebSocketClient webSocketClient;
    private DataRepository dataRepository;
    private boolean isStreaming = false;
    private boolean isConnected = false;
    private boolean shouldReconnect = true;
    private boolean isLoggedIn = false;

    // Thread pool for background processing
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Audio data stream
    private final MutableLiveData<byte[]> incomingAudio = new MutableLiveData<>();

    // Connection retry counter
    private int reconnectAttempts = 0;

    // User session data
    private static class UserData {
        String userId = "";
        String userName = "";
        String password = "";
        boolean newChat = true;
        String personality = "";
        String channel = "";
    }

    private final UserData userData = new UserData();

    /**
     * Default constructor
     */
    public WebSocketHandler() {
        // Initialize but don't connect yet
    }

    /**
     * Set server connection config
     */
    public void setServerConfig(String host, int wsPort, int loginPort) {
        this.serverHost = host;
        this.wsPort = wsPort;
        this.loginPort = loginPort;
        this.wsUrl = "ws://" + host + ":" + wsPort;
    }
    public boolean isStreaming(){
        return isStreaming;
    }

    /**
     * Set the data repository reference
     * @param dataRepository Repository for data operations
     */
    public void setDataRepository(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    /**
     * Set user data for session
     */
    public void setUserData(String userId, String userName, String password,
                            boolean newChat, String personality, String channel) {
        this.userData.userId = userId;
        this.userData.userName = userName;
        this.userData.password = password;
        this.userData.newChat = newChat;
        this.userData.personality = personality;
        this.userData.channel = channel;
    }

    /**
     * Send user information to the server
     */
    @Override
    public void sendUserInfo(String userName, String userId, String personality, String channel) {
        setUserData(userId, userName, "", true, personality, channel);

        if (isConnected && webSocketClient != null && webSocketClient.isOpen()) {
            sendUserIdentity();
        }
    }

    /**
     * Start audio streaming to server
     */
    @Override
    public void startStreaming() {
        if (!isConnected) {
            Log.d(TAG, "Not connected, attempting connection before streaming");
            connect();
        }

        isStreaming = true;
        Log.d(TAG, "Audio streaming started");

        try {
            // Notify server to start streaming
            JSONObject message = new JSONObject();
            message.put("type", "input_audio_buffer.start");

            if (webSocketClient != null && webSocketClient.isOpen()) {
                webSocketClient.send(message.toString());
                Log.d(TAG, "Sent input_audio_buffer.start message");

                // Update status to streaming
                if (dataRepository != null) {
                    dataRepository.updateStatus(new StatusUpdate("listening"));
                }
            } else {
                Log.e(TAG, "WebSocket not ready, cannot send streaming start message");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending start streaming message", e);
        }
    }

    /**
     * Stop audio streaming to server
     */
    @Override
    public void stopStreaming() {
        if (isStreaming) {
            isStreaming = false;
            Log.d(TAG, "Audio streaming stopped");

            if (dataRepository != null) {
                dataRepository.updateStatus(new StatusUpdate("stop_streaming"));
            }

            try {
                if (webSocketClient != null && webSocketClient.isOpen()) {
                    JSONObject message = new JSONObject();
                    message.put("type", "input_audio_buffer.stop");
                    webSocketClient.send(message.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending stop streaming message", e);
            }
        }
    }

    /**
     * Get LiveData for incoming audio
     * @return LiveData stream of audio data
     */
    @Override
    public LiveData<byte[]> getIncomingAudio() {
        return incomingAudio;
    }

    /**
     * Send audio data to server
     * @param audioData Raw audio data as byte array
     */
    @Override
    public void sendAudioData(byte[] audioData) {
        // 新增USER_ID
        if (audioData == null || audioData.length == 0) {
            return;
        }

        // 如果正在播放，忽略錄音輸入
        if (dataRepository != null && dataRepository.getPlaybackStatus().getValue() == Boolean.TRUE) {
            Log.d(TAG, "Ignoring audio input during playback to prevent feedback");
            return;
        }

        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject message = new JSONObject();
                message.put("type", "input_audio_buffer.append");
                message.put("audio", Base64.encodeToString(audioData, Base64.DEFAULT));
                webSocketClient.send(message.toString());
                Log.d(TAG, "Sent audio data: " + audioData.length + " bytes");
            } catch (Exception e) {
                Log.e(TAG, "Error sending audio data", e);
            }
        } else {
            Log.w(TAG, "WebSocket not connected, cannot send audio data");
        }
    }

    /**
     * Attempt to reconnect to the server
     */
    private void attemptReconnect() {
        if (!isConnected && shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            Log.d(TAG, "Attempting to reconnect (attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");

            // Reset WebSocketClient - can't reuse old ones after they're closed
            webSocketClient = null;
            connect();
        } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnection attempts reached");
            if (dataRepository != null) {
                dataRepository.updateStatus(new StatusUpdate("error", "Failed to connect after " + MAX_RECONNECT_ATTEMPTS + " attempts"));
            }
        }
    }

    /**
     * Send text message to server
     * @param message Text message to send
     */
    @Override
    public void sendMessage(String message) {
        if (message == null || message.isEmpty()) {
            Log.w(TAG, "Cannot send empty message");
            return;
        }

        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject event = new JSONObject();
                event.put("type", "input.message");
                event.put("message", message);
                event.put("timestamp", System.currentTimeMillis());
                webSocketClient.send(event.toString());
                Log.d(TAG, "Text message sent: " + message);
            } catch (Exception e) {
                Log.e(TAG, "Error sending text message", e);
                if (dataRepository != null) {
                    dataRepository.updateStatus(new StatusUpdate("error", "Failed to send message: " + e.getMessage()));
                }
            }
        } else {
            Log.w(TAG, "WebSocket not connected, cannot send message");
            attemptReconnect();

            // Store message to send after connection
            if (dataRepository != null) {
                dataRepository.updateStatus(new StatusUpdate("queued", message));
            }
        }
    }

    /**
     * Send message with image to server
     * @param message Text message
     * @param imageBase64 Base64 encoded image
     */
    @Override
    public void sendMessageWithImage(String message, String imageBase64) {
        if (message == null || imageBase64 == null) {
            Log.w(TAG, "Cannot send message with invalid image data");
            return;
        }

        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject event = new JSONObject();
                event.put("type", "input.message.with.image");
                event.put("message", message);
                event.put("image", imageBase64);
                event.put("timestamp", System.currentTimeMillis());
                webSocketClient.send(event.toString());
                Log.d(TAG, "Message with image sent");
            } catch (Exception e) {
                Log.e(TAG, "Error sending message with image", e);
                if (dataRepository != null) {
                    dataRepository.updateStatus(new StatusUpdate("error", "Failed to send image: " + e.getMessage()));
                }
            }
        } else {
            Log.w(TAG, "WebSocket not connected, cannot send message with image");
            attemptReconnect();
        }
    }

    /**
     * Modified login method to match Python client flow.
     * First establishes WebSocket connection, then does TCP login.
     */
    @Override
    public boolean login(String userId, String userName, String password, boolean newChat, String personality) {
        try {
            Log.d(TAG, "Processing login request: " + userName);

            // Save session information
            setUserData(userId, userName, password, newChat, personality, "");

            // Step 1: Establish WebSocket connection first
            if (!isConnected) {
                establishWebSocketConnection();

                // Give WebSocket connection time to establish
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Sleep interrupted", e);
                }
            }

            // Step 2: Perform TCP login after WebSocket is connected
            boolean loginSuccess = loginWithAccount(userId, userName, password, newChat, personality);

            if (loginSuccess) {
                isLoggedIn = true;

                if (dataRepository != null) {
                    dataRepository.updateStatus(new StatusUpdate("login_success"));
                }

                // No need to send user identity since server already knows who we are
                // from the TCP login
            } else {
                if (dataRepository != null) {
                    dataRepository.updateStatus(new StatusUpdate("login_failed", "Server rejected login"));
                }
            }

            return loginSuccess;
        } catch (Exception e) {
            Log.e(TAG, "Error during login process: " + e.getMessage(), e);
            if (dataRepository != null) {
                dataRepository.updateStatus(new StatusUpdate("login_failed", e.getMessage()));
            }
            return false;
        }
    }

    /**
     * 執行帳號密碼驗證
     */
    private boolean loginWithAccount(String userId, String userName, String password,
                                     boolean newChat, String mbti) {
        try {
            // 創建登入請求
            JSONObject body = new JSONObject();
            body.put("id", "41b7d2ce-073a-4312-b410-137a995ca8a8");
            body.put("user_name", userName);
            body.put("password", password);
            body.put("new_chat", newChat);
            body.put("mbti", mbti);

            String jsonString = body.toString();
            Log.d(TAG, "發送登入請求: " + jsonString);

            // 建立連接並發送數據
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(serverHost, loginPort), CONNECTION_TIMEOUT_MS);
            socket.getOutputStream().write(jsonString.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            Log.d(TAG, "登入請求已發送");

            // 讀取服務器響應
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response = reader.readLine();
            Log.d(TAG, "接收到登入響應: " + response);

            // 解析響應
            JSONObject responseJson = new JSONObject(response);
            boolean isLoginSuccess = responseJson.getBoolean("is_login");

            // 關閉連接
            socket.close();

            // 更新用戶數據
            if (isLoginSuccess) {
                this.userData.userId = "41b7d2ce-073a-4312-b410-137a995ca8a8";
                this.userData.userName = userName;
                this.userData.password = password;
                this.userData.newChat = newChat;
                this.userData.personality = mbti;
            }

            return isLoginSuccess;
        } catch (Exception e) {
            Log.e(TAG, "登入失敗: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Establish WebSocket connection
     */
    private void establishWebSocketConnection() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            Log.d(TAG, "WebSocket already connected, no need to reconnect");
            return;
        }

        try {
            URI uri = new URI(wsUrl);
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    isConnected = true;
                    reconnectAttempts = 0;
                    Log.d(TAG, "WebSocket connection successful with status: " + handshake.getHttpStatus());

                    // Notify connection status update
                    if (dataRepository != null) {
                        mainHandler.post(() -> dataRepository.updateStatus(new StatusUpdate("connected")));
                    }
                }

                @Override
                public void onMessage(String message) {
                    executorService.execute(() -> handleMessage(message));
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    isConnected = false;
                    Log.d(TAG, "WebSocket connection closed: " + reason + " (code: " + code + ", remote: " + remote + ")");

                    if (dataRepository != null) {
                        mainHandler.post(() -> dataRepository.updateStatus(new StatusUpdate("disconnected", reason)));
                    }

                    // Attempt to reconnect if this wasn't a client-initiated closure
                    if (shouldReconnect) {
                        mainHandler.postDelayed(() -> attemptReconnect(), RECONNECT_DELAY_MS);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage(), ex);
                    if (dataRepository != null) {
                        mainHandler.post(() -> dataRepository.updateStatus(new StatusUpdate("error", ex.getMessage())));
                    }
                }
            };

            // Update connection status
            if (dataRepository != null) {
                dataRepository.updateStatus(new StatusUpdate("connecting"));
            }

            // Configure and connect
            webSocketClient.setConnectionLostTimeout(0);
            webSocketClient.setTcpNoDelay(true);
            webSocketClient.connect();

        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid WebSocket URI", e);
            if (dataRepository != null) {
                dataRepository.updateStatus(new StatusUpdate("error", "Invalid WebSocket URI: " + e.getMessage()));
            }
        }
    }

    /**
     * Send user identity to WebSocket server
     * Not needed in the updated flow, but kept for compatibility
     */
    private void sendUserIdentity() {
        if (!isLoggedIn || webSocketClient == null || !webSocketClient.isOpen()) {
            return;
        }

        try {
            JSONObject userLoginMessage = new JSONObject();
            userLoginMessage.put("type", "user.login");
            userLoginMessage.put("user_id", userData.userId);
            userLoginMessage.put("user_name", userData.userName);
            userLoginMessage.put("personality", userData.personality);
            userLoginMessage.put("new_chat", userData.newChat);
            webSocketClient.send(userLoginMessage.toString());
            Log.d(TAG, "User identity sent to WebSocket");
        } catch (Exception e) {
            Log.e(TAG, "Error sending user identity", e);
        }
    }

    /**
     * Connect or reconnect to server
     */
    @Override
    public void connect() {
        // First check if we need to login
        if (!isLoggedIn) {
            Log.w(TAG, "Not logged in, attempting to connect anyway");
        }

        establishWebSocketConnection();
    }

    /**
     * Handle incoming messages from the WebSocket
     * @param message JSON string from the server
     */
    private void handleMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        try {
            JSONObject event = new JSONObject(message);
            String type = event.getString("type");

            switch (type) {
                case "response.audio.delta": {
                    // 常規音頻數據塊
                    String audioBase64 = event.getString("delta");
                    byte[] audioData = Base64.decode(audioBase64, Base64.DEFAULT);

                    // 立即分派到主線程
                    mainHandler.post(() -> {
                        if (dataRepository != null) {
                            dataRepository.handleIncomingAudio(audioData);
                        }
                    });
                    break;
                }
                case "response.audio.done": {
                    // 服務器指示音頻響應完成
                    Log.d(TAG, "Server indicated audio response completion");
                    break;
                }

                case "interrupt.audio": {
                    // Server requested audio interrupt
                    mainHandler.post(() -> {
                        if (dataRepository != null) {
                            dataRepository.updateStatus(new StatusUpdate("interrupt_audio"));
                        }
                    });
                    break;
                }

                case "interrupt.audio.with.new": {
                    // Server sent replacement audio
                    String audioBase64 = event.getString("new_audio");
                    byte[] audioData = Base64.decode(audioBase64, Base64.DEFAULT);

                    mainHandler.post(() -> {
                        if (dataRepository != null) {
                            // First send interrupt status
                            dataRepository.updateStatus(new StatusUpdate("interrupt_audio", null));

                            // Send new audio after a short delay
                            new Handler(Looper.getMainLooper()).postDelayed(() -> dataRepository.handleIncomingAudio(audioData), 100);
                        }
                    });
                    break;
                }

                case "response.emotion_transcript": {
                    // Response with emotion and transcript
                    String emotion = event.getString("emotion");
                    String transcript = event.getString("transcript");

                    // Set playback status to false (done playing)
                    if (dataRepository != null) {
                        dataRepository.setPlaybackStatus(false);
                    }

                    // Update robot state (speaking + emotion)
                    mainHandler.post(() -> {
                        if (dataRepository != null) {
                            StatusUpdate statusUpdate = new StatusUpdate("speaking", emotion, transcript);
                            dataRepository.updateStatus(statusUpdate);
                        }
                    });
                    break;
                }

                case "conversation.item.input_audio_transcription.completed": {
                    // Speech recognition complete
                    String transcript = event.getString("transcript");
                    Log.d(TAG, "Speech recognition result: " + transcript);

                    mainHandler.post(() -> {
                        if (dataRepository != null) {
                            dataRepository.updateStatus(new StatusUpdate("recognized", transcript));
                        }
                    });
                    break;
                }

                case "user.login.success": {
                    Log.d(TAG, "User login successful");
                    mainHandler.post(() -> {
                        if (dataRepository != null) {
                            dataRepository.updateStatus(new StatusUpdate("login_success"));
                        }
                    });
                    break;
                }

                case "user.login.failed": {
                    String errorMessage = event.optString("message", "Unknown error");
                    Log.e(TAG, "User login failed: " + errorMessage);
                    mainHandler.post(() -> {
                        if (dataRepository != null) {
                            dataRepository.updateStatus(new StatusUpdate("login_failed", errorMessage));
                        }
                    });
                    break;
                }

                case "ping": {
                    // Server ping - respond with pong to maintain connection
                    try {
                        JSONObject pongMessage = new JSONObject();
                        pongMessage.put("type", "pong");
                        pongMessage.put("timestamp", System.currentTimeMillis());
                        webSocketClient.send(pongMessage.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending pong response", e);
                    }
                    break;
                }

                default:
                    Log.d(TAG, "Received unknown event type: " + type);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing message: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error handling message", e);
        }
    }


    /**
     * Disconnect from WebSocket server
     * Stops auto-reconnect
     */
    @Override
    public void disconnect() {
        shouldReconnect = false;
        stopStreaming();

        if (webSocketClient != null) {
            try {
                // Send a clean close message
                JSONObject closeMsg = new JSONObject();
                closeMsg.put("type", "client.disconnect");
                webSocketClient.send(closeMsg.toString());

                // Close connection
                webSocketClient.close();
            } catch (Exception e) {
                Log.e(TAG, "Error during disconnect", e);
            }
            isConnected = false;
            Log.d(TAG, "WebSocket disconnected by client request");
        }
    }

    /**
     * Check connection status
     * @return true if connected, false otherwise
     */
    @Override
    public boolean isConnected() {
        return isConnected && webSocketClient != null && webSocketClient.isOpen();
    }

    /**
     * Clean up resources when no longer needed
     * Called from onCleared of ViewModel
     */
    public void cleanup() {
        // Stop streaming and disconnect
        disconnect();

        // Shutdown executor service
        executorService.shutdown();
        Log.d(TAG, "WebSocketHandler resources cleaned up");
    }
}