package com.example.videocall

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import android.util.Log
import org.json.JSONObject
import android.content.BroadcastReceiver
import android.content.Context

class MainActivity : AppCompatActivity() {

    private lateinit var etMyId: EditText
    private lateinit var btnRegister: Button
    private lateinit var etCallId: EditText
    private lateinit var btnCall: Button
    private lateinit var btnAnswer: Button
    private lateinit var btnDecline: Button
    private lateinit var tvStatus: TextView
    private lateinit var rvOnlineUsers: RecyclerView
    private lateinit var usersAdapter: OnlineUsersAdapter

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val WEBSOCKET_URL = "wss://sociallab.duckdns.org/videoCall/"

    private var myId: String = ""
    private var isRegistered = false
    private var incomingCall: String = ""

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "VIDEO_CALL_ENDED") {
                Log.d("MainActivity", "收到通話結束廣播，重新初始化 WebSocket")
                // 重新初始化 WebSocket 連接
                initWebSocket()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "App 啟動")
        setContentView(R.layout.activity_main)

        // 請求權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 1)
        }

        initViews()
        initWebSocket()
        setupClickListeners()
        setupRecyclerView()

        // 註冊廣播接收器
        val filter = IntentFilter("VIDEO_CALL_ENDED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callEndedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onResume() {
        super.onResume()
        // 當回到 MainActivity 時，檢查是否需要重新連接
        if (isRegistered && webSocket?.request()?.url?.toString() != WEBSOCKET_URL) {
            Log.d("MainActivity", "onResume: 重新建立 WebSocket 連接")
            initWebSocket()
        }
    }

    private fun initViews() {
        etMyId = findViewById(R.id.etMyId)
        btnRegister = findViewById(R.id.btnRegister)
        etCallId = findViewById(R.id.etCallId)
        btnCall = findViewById(R.id.btnCall)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnDecline = findViewById(R.id.btnDecline)
        tvStatus = findViewById(R.id.tvStatus)
        rvOnlineUsers = findViewById(R.id.rvOnlineUsers)

        // 預設生成一個隨機ID
        etMyId.setText("User${(1000..9999).random()}")

        updateUIState()
    }

    private fun setupRecyclerView() {
        usersAdapter = OnlineUsersAdapter { userId ->
            // 點擊用戶後自動填入通話ID
            etCallId.setText(userId)
        }
        rvOnlineUsers.layoutManager = LinearLayoutManager(this)
        rvOnlineUsers.adapter = usersAdapter
    }

    private fun setupClickListeners() {
        btnRegister.setOnClickListener {
            if (!isRegistered) {
                registerUser()
            } else {
                unregisterUser()
            }
        }

        btnCall.setOnClickListener {
            makeCall()
        }

        btnAnswer.setOnClickListener {
            answerCall()
        }

        btnDecline.setOnClickListener {
            declineCall()
        }
    }

    private fun initWebSocket() {
        // 先關閉現有連接
        webSocket?.close(1000, "Reconnecting")

        val request = Request.Builder().url(WEBSOCKET_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runOnUiThread {
                    tvStatus.text = "已連接到服務器"
                    Log.d("MainActivity", "WebSocket 連接成功")

                    // 如果之前已註冊，重新註冊
                    if (isRegistered && myId.isNotEmpty()) {
                        Log.d("MainActivity", "重新註冊用戶: $myId")
                        registerUser()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    handleSignalingMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    tvStatus.text = "連接失敗: ${t.message}"
                    Log.e("MainActivity", "WebSocket 連接失敗", t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    tvStatus.text = "連接已關閉"
                    Log.d("MainActivity", "WebSocket 連接關閉: $reason")
                }
            }
        })
    }

    private fun handleSignalingMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")

            when (type) {
                "register_success" -> {
                    isRegistered = true
                    myId = json.getString("id")
                    tvStatus.text = "已註冊: $myId"
                    updateUIState()
                    Log.d("MainActivity", "註冊成功: $myId")
                }

                "register_error" -> {
                    val errorMessage = json.getString("message")
                    tvStatus.text = "註冊失敗: $errorMessage"
                    Log.e("MainActivity", "註冊失敗: $errorMessage")
                }

                "user_list" -> {
                    val users = json.getJSONArray("users")
                    val userList = mutableListOf<String>()
                    for (i in 0 until users.length()) {
                        val userId = users.getString(i)
                        if (userId != myId) {
                            userList.add(userId)
                        }
                    }
                    usersAdapter.updateUsers(userList)
                }

                "incoming_call" -> {
                    incomingCall = json.getString("from")
                    tvStatus.text = "來電: $incomingCall"
                    updateUIState()
                }

                "call_accepted" -> {
                    val peerId = json.getString("peer")
                    startVideoCall(peerId, true) // 作為呼叫方
                }

                "call_declined" -> {
                    tvStatus.text = "通話被拒絕"
                    updateUIState()
                }

                "user_offline" -> {
                    val userId = json.getString("user")
                    tvStatus.text = "$userId 已下線"
                }
            }
        } catch (e: Exception) {
            tvStatus.text = "訊息處理錯誤: ${e.message}"
            Log.e("MainActivity", "訊息處理錯誤", e)
        }
    }

    private fun registerUser() {
        val userId = if (myId.isNotEmpty()) myId else etMyId.text.toString().trim()

        if (userId.isEmpty()) {
            Toast.makeText(this, "請輸入用戶ID", Toast.LENGTH_SHORT).show()
            return
        }

        val message = JSONObject().apply {
            put("type", "register")
            put("id", userId)
        }

        webSocket?.send(message.toString())
        tvStatus.text = "註冊中..."
        Log.d("MainActivity", "發送註冊請求: $userId")
    }

    private fun unregisterUser() {
        val message = JSONObject().apply {
            put("type", "unregister")
            put("id", myId)
        }

        webSocket?.send(message.toString())
        isRegistered = false
        myId = ""
        usersAdapter.updateUsers(emptyList())
        updateUIState()
    }

    private fun makeCall() {
        val callId = etCallId.text.toString().trim()
        Log.d("WebSocket", "嘗試撥打: $callId")

        if (callId.isEmpty()) {
            Toast.makeText(this, "請輸入要撥打的ID", Toast.LENGTH_SHORT).show()
            return
        }

        val message = JSONObject().apply {
            put("type", "call")
            put("from", myId)
            put("to", callId)
        }

        webSocket?.send(message.toString())
        Log.d("WebSocket", "已發送通話請求: ${message}")
    }

    private fun answerCall() {
        val message = JSONObject().apply {
            put("type", "call_answer")
            put("from", myId)
            put("to", incomingCall)
        }

        webSocket?.send(message.toString())
        startVideoCall(incomingCall, false) // 作為接聽方
    }

    private fun declineCall() {
        val message = JSONObject().apply {
            put("type", "call_decline")
            put("from", myId)
            put("to", incomingCall)
        }

        webSocket?.send(message.toString())
        incomingCall = ""
        tvStatus.text = "已拒絕通話"
        updateUIState()
    }

    private fun startVideoCall(peerId: String, isCaller: Boolean) {
        val intent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra("PEER_ID", peerId)
            putExtra("MY_ID", myId)
            putExtra("IS_CALLER", isCaller)
            putExtra("WEBSOCKET_URL", WEBSOCKET_URL)
        }
        startActivity(intent)

        incomingCall = ""
        updateUIState()
    }

    private fun updateUIState() {
        btnRegister.text = if (isRegistered) "取消註冊" else "註冊"
        btnCall.isEnabled = isRegistered && incomingCall.isEmpty()
        btnAnswer.isEnabled = incomingCall.isNotEmpty()
        btnDecline.isEnabled = incomingCall.isNotEmpty()
        etMyId.isEnabled = !isRegistered
        etCallId.isEnabled = isRegistered && incomingCall.isEmpty()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "相機權限已授予")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "取消註冊廣播接收器失敗", e)
        }
        webSocket?.close(1000, "App closed")
    }
}