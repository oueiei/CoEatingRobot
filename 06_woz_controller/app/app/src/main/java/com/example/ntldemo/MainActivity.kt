package com.example.ntldemo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject

// 機器人相關
import com.nuwarobotics.service.IClientId
import com.nuwarobotics.service.agent.NuwaRobotAPI
import com.nuwarobotics.service.agent.RobotEventListener
import com.nuwarobotics.service.agent.VoiceEventListener


class MainActivity : AppCompatActivity() {

    var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    lateinit var websocketUrl: String

    companion object {
        const val PREF_NAME = "woz_controller_prefs"
        const val DEFAULT_URL = "wss://sociallab.duckdns.org/ntl_demo/"
    }

    // 公開屬性供 Fragment 使用
    var myId: String = ""
    var originalId = ""
    var assignedRole: String = ""
    var isRegistered = false
    var isControlMode = false
    var isWebSocketConnected = false

    private var isInVideoCall = false

    // NuwaRobotAPI
    private lateinit var mRobotAPI: NuwaRobotAPI
    private lateinit var mClientId: IClientId
    private val motionMap = mapOf(
        "揮手" to "666_SA_Discover",
        "點頭" to "666_PE_PushGlasses",
        "敬禮" to "666_RE_Ask",
        "雙手擺動" to "666_SA_Think",
        "鞠躬" to "666_RE_Bye",
        "wave" to "666_SA_Discover",
        "nod" to "666_PE_PushGlasses",
        "salute" to "666_RE_Ask",
        "bow" to "666_RE_Bye",
        "raise_hand" to "666_SA_Discover",
        "lower_hand" to "666_RE_Bye"
    )
    private lateinit var emotionVideoMap: Map<String, String>
    enum class FragmentType {
        MAIN, CONTROL_MODE, VIDEO_CALL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 1)
        }

        emotionVideoMap =mapOf(
            // 英文鍵值 (對應中文鍵值)
            "idling" to "android.resource://${packageName}/${R.raw.e_neutral_n}",
            "thinking" to "android.resource://${packageName}/${R.raw.e_thinking_n}",
            "listening" to "android.resource://${packageName}/${R.raw.e_listening_n}",
            "error" to "android.resource://${packageName}/${R.raw.e_sad_n}",
            "reset" to "android.resource://${packageName}/${R.raw.e_neutral_n}",
            "neutral" to "android.resource://${packageName}/${R.raw.e_neutral_s}",
            "angry" to "android.resource://${packageName}/${R.raw.e_angry_s}",
            "joy" to "android.resource://${packageName}/${R.raw.e_joy_s}",
            "sad" to "android.resource://${packageName}/${R.raw.e_sad_s}",
            "surprise" to "android.resource://${packageName}/${R.raw.i_surprise_s}",
            "scared" to "android.resource://${packageName}/${R.raw.e_scared_s}",
            "disgusted" to "android.resource://${packageName}/${R.raw.e_disgusted_s}",
            "excited" to "android.resource://${packageName}/${R.raw.e_excited_s}"

        )

        // 從 SharedPreferences 載入 Server URL
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        websocketUrl = prefs.getString("server_url", DEFAULT_URL) ?: DEFAULT_URL

        initNuwaRobotAPI()
        initWebSocket()

        // 載入主界面 Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainFragment())
                .commit()
        }
    }

    private fun initNuwaRobotAPI() {
        try {
            mClientId = IClientId(this.packageName)
            mRobotAPI = NuwaRobotAPI(this, mClientId)
            mRobotAPI.registerRobotEventListener(customRobotEventListener)
            mRobotAPI.registerVoiceEventListener(customVoiceEventListener)
            Log.d("MainActivity", "NuwaRobotAPI initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize NuwaRobotAPI", e)
        }
    }

    private val customRobotEventListener = object : RobotEventListener {
        override fun onWikiServiceStart() {}
        override fun onWikiServiceStop() {}
        override fun onWikiServiceCrash() {}
        override fun onWikiServiceRecovery() {}

        override fun onStartOfMotionPlay(motion: String?) {
            Log.d("RobotEvent", "Motion started: $motion")
            sendStatusToServer("motion_start", motion ?: "unknown", "busy")

            updateCurrentCommand("動作開始: $motion")

        }

        override fun onPauseOfMotionPlay(p0: String?) {}
        override fun onStopOfMotionPlay(p0: String?) {}

        override fun onCompleteOfMotionPlay(motion: String?) {
            Log.d("RobotEvent", "Motion completed: $motion")
            sendStatusToServer("motion_complete", motion ?: "unknown", "ready")
            updateCurrentCommand("動作完成: $motion")
        }

        override fun onErrorOfMotionPlay(errorCode: Int) {
            Log.e("RobotEvent", "Motion error: $errorCode")
            sendStatusToServer("motion_error", errorCode.toString(), "error")
        }

        override fun onPlayBackOfMotionPlay(p0: String?) {}
        override fun onPrepareMotion(p0: Boolean, p1: String?, p2: Float) {}
        override fun onCameraOfMotionPlay(p0: String?) {}
        override fun onGetCameraPose(p0: Float, p1: Float, p2: Float, p3: Float, p4: Float, p5: Float, p6: Float, p7: Float, p8: Float, p9: Float, p10: Float, p11: Float) {}
        override fun onTouchEvent(p0: Int, p1: Int) {}
        override fun onPIREvent(p0: Int) {}
        override fun onTap(p0: Int) {}
        override fun onLongPress(p0: Int) {}
        override fun onWindowSurfaceReady() {}
        override fun onWindowSurfaceDestroy() {}
        override fun onTouchEyes(p0: Int, p1: Int) {}
        override fun onRawTouch(p0: Int, p1: Int, p2: Int) {}
        override fun onFaceSpeaker(p0: Float) {}
        override fun onActionEvent(p0: Int, p1: Int) {}
        override fun onDropSensorEvent(p0: Int) {}
        override fun onMotorErrorEvent(p0: Int, p1: Int) {}
    }

    private val customVoiceEventListener = object : VoiceEventListener {
        override fun onTTSComplete(isError: Boolean) {
            Log.d("VoiceEvent", "TTS Complete, error: $isError")
            val status = if (isError) "error" else "ready"
            sendStatusToServer("tts_complete", if (isError) "error" else "success", status)

            // 停止 TTS 表情，回到預設 idling
            val fragment = getCurrentFragment() as? ControlModeFragment
            fragment?.stopTTSEmotion()

            updateCurrentCommand(if (isError) "TTS錯誤" else "TTS完成")
        }

        override fun onWakeup(isError: Boolean, score: String?, direction: Float) {}
        override fun onSpeechRecognizeComplete(isError: Boolean, resultType: VoiceEventListener.ResultType?, json: String?) {}
        override fun onSpeech2TextComplete(isError: Boolean, json: String?) {}
        override fun onMixUnderstandComplete(isError: Boolean, resultType: VoiceEventListener.ResultType?, s: String?) {}
        override fun onSpeechState(listenType: VoiceEventListener.ListenType?, speechState: VoiceEventListener.SpeechState?) {}
        override fun onSpeakState(speakType: VoiceEventListener.SpeakType?, speakState: VoiceEventListener.SpeakState?) {}
        override fun onGrammarState(isError: Boolean, s: String?) {}
        override fun onListenVolumeChanged(listenType: VoiceEventListener.ListenType?, i: Int) {}
        override fun onHotwordChange(hotwordState: VoiceEventListener.HotwordState?, hotwordType: VoiceEventListener.HotwordType?, s: String?) {}
    }

    private fun sendStatusToServer(eventType: String, data: String, status: String = "ready") {
        if (isWebSocketConnected && webSocket != null) {
            val message = JSONObject().apply {
                put("type", "robot_status")
                put("robotId", myId)
                put("status", status)
                put("eventType", eventType)
                put("data", data)
                put("assignedRole", assignedRole)
                put("battery", 85)
                put("timestamp", System.currentTimeMillis())
            }
            webSocket?.send(message.toString())
            Log.d("WebSocket", "Status sent: $eventType - $data - $status")
        }
    }

    fun initWebSocket() {
        webSocket?.close(1000, "Reconnecting")

        val request = Request.Builder().url(websocketUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isWebSocketConnected = true
                runOnUiThread {
                    updateConnectionStatus(true)
                    if (myId.isEmpty()) {
                        myId = "Robot_${(1000..9999).random()}"
                        originalId = myId
                    }
                    registerWithId(myId)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    handleServerMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isWebSocketConnected = false
                runOnUiThread {
                    updateConnectionStatus(false)
                    updateStatus("錯誤: ${t.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isWebSocketConnected = false
                runOnUiThread {
                    updateConnectionStatus(false)
                    updateStatus("連接關閉")
                }
            }
        })
    }

    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            Log.d("server", "$type and $message")

            when (type) {
                "register_success" -> {
                    isRegistered = true
                    myId = json.getString("id")
                    updateStatus("已註冊: $myId")
                    updateRegistrationStatus(true)
                }

                "roles_assigned" -> {
                    val assignments = json.getJSONObject("assignments")
                    if (assignments.has(myId)) {
                        assignedRole = assignments.getString(myId)
                        updateRoleStatus(assignedRole)
                        updateStatus("角色已分配，等待控制指令")
                        sendStatusToServer("role_assigned", assignedRole, "ready")
                    }
                }

                "control_mode_started" -> {
                    isControlMode = true
                    showFragment(FragmentType.CONTROL_MODE)
                    sendStatusToServer("control_mode_active", "face_shown", "ready")
                }

                "control_mode_stopped" -> {
                    isControlMode = false
                    showFragment(FragmentType.MAIN)
                    sendStatusToServer("control_mode_inactive", "face_hidden", "ready")
                }

                "robot_command" -> {
                    executeRobotCommand(json)
                }

                "play_line" -> {
                    executeScriptLine(json)
                }

                "start_video_call" -> {
                    val from = json.optString("from", "")
                    val to = json.optString("to", "")

                    Log.d("MainActivity", "Video call: from=$from, to=$to, myRole=$assignedRole")
                    isInVideoCall = true
                    val extraData = Bundle().apply { putString("peerId", from) }
                    showFragment(FragmentType.VIDEO_CALL, extraData)
                }

                "end_video_call" -> {
                    if (isInVideoCall) {
                        isInVideoCall = false
                        val targetType = if (isControlMode) FragmentType.CONTROL_MODE else FragmentType.MAIN
                        showFragment(targetType)
                    }
                }
                "offer", "answer", "ice_candidate" -> {
                    val fragment = getCurrentFragment()
                    if (fragment is VideoCallFragment) {
                        // 直接在主線程處理，不需要額外方法
                        fragment.handleSignalingMessage(message)
                    }
                }

                "register_error" -> {
                    updateStatus("註冊失敗: ${json.getString("message")}")
                }

                "role_cleared" -> {
                    val robotId = json.getString("robotId")
                    if (robotId == myId) {
                        assignedRole = ""
                        updateRoleStatus("")
                        updateStatus("等待角色分配")
                    }
                }

                "all_roles_reset" -> {
                    assignedRole = ""
                    updateRoleStatus("")
                    updateStatus("等待角色分配")
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocket", "訊息處理錯誤", e)
            updateStatus("訊息處理錯誤: ${e.message}")
        }
    }

    private fun executeScriptLine(json: JSONObject) {
        try {
            val scriptId = json.optString("scriptId", "")
            val lineId = json.optInt("lineId", 0)
            val lineData = json.optJSONObject("line")
            val targetRobots = json.optJSONArray("targetRobots")

            if (lineData != null && targetRobots != null) {
                val speaker = lineData.optString("speaker", "")
                val content = lineData.optString("content", "")
                val actions = lineData.optJSONArray("actions")

                // 檢查這個機器人是否應該執行這行
                var shouldExecute = false
                for (i in 0 until targetRobots.length()) {
                    if (targetRobots.getString(i) == assignedRole) {
                        shouldExecute = true
                        break
                    }
                }

                if (shouldExecute) {
                    Log.d("ScriptExecution", "執行台詞: $speaker - $content")
                    updateCurrentCommand("執行台詞: $content")

                    if (content.isNotEmpty()) {
                        val fragment = getCurrentFragment() as? ControlModeFragment
                        fragment?.startTTSEmotion()
                        mRobotAPI.startTTS(content)  // 直接使用傳入的 content
                        sendStatusToServer("script_tts_start", "script_$scriptId: line_$lineId: $content", "speaking")
                    }

                    // 處理動作...
                    actions?.let { actionsArray ->
                        for (i in 0 until actionsArray.length()) {
                            val action = actionsArray.getJSONObject(i)
                            val actionType = action.optString("type", "")
                            val actionValue = action.optString("value", "")

                            when (actionType) {
                                "gesture" -> {
                                    performMotion(actionValue)
                                    sendStatusToServer("script_gesture", "line_$lineId: $actionValue", "busy")
                                }
                                "emotion" -> {
                                    val fragment = getCurrentFragment() as? ControlModeFragment
                                    fragment?.playEmotionByAction(actionValue)
                                    sendStatusToServer("script_emotion", "line_$lineId: $actionValue", "busy")
                                }
                                "video_call" -> {
                                    showFragment(FragmentType.VIDEO_CALL)
                                }
                            }
                        }
                    }
                } else {
                    Log.d("ScriptExecution", "Line $lineId not for this robot ($assignedRole)")
                }
            }
        } catch (e: Exception) {
            Log.e("ScriptExecution", "腳本執行錯誤", e)
            sendStatusToServer("script_error", e.message ?: "unknown_error", "error")
        }
    }

    private fun executeRobotCommand(json: JSONObject) {
        try {
            val targetRobot = json.optString("targetRobot", "")
            val action = json.getString("action")
            val content = json.getString("content")
            val parameters = json.optJSONObject("parameters")

            if (targetRobot != assignedRole && targetRobot.isNotEmpty()) {
                Log.d("RobotCommand", "指令不是給我的 (${assignedRole}): $targetRobot")
                return
            }


            updateCurrentCommand("執行: $action - $content")


            when (action) {
                "speak" -> {
                    mRobotAPI.startTTS(content)
                    sendStatusToServer("command_tts", "$action:$content", "speaking")
                }

                "gesture" -> {
                    val gestureType = parameters?.optString("gesture") ?: content
                    performMotion(gestureType)
                    sendStatusToServer("command_gesture", "$action:$gestureType", "busy")
                }

                "move" -> {
                    Log.d("RobotCommand", "Move command: $content")
                    sendStatusToServer("command_move", "$action:$content", "moving")
                }

                "expression" -> {
                    Log.d("RobotCommand", "Expression command: $content")
                    sendStatusToServer("command_expression", "$action:$content", "busy")
                }

                "stop" -> {
                    mRobotAPI.motionStop(true)
                    mRobotAPI.stopTTS()

                    updateCurrentCommand("所有動作已停止")

                    sendStatusToServer("command_stop", "all_actions_stopped", "ready")
                }
            }

        } catch (e: Exception) {
            Log.e("RobotCommand", "執行指令錯誤", e)
            sendStatusToServer("command_error", e.message ?: "unknown_error", "error")
        }
    }

    // 初始化表情影片路徑
    fun getEmotionVideoMap(): Map<String, String> = emotionVideoMap

    // 動作控制的方法
    fun performMotion(motionId: String) {
        if (::mRobotAPI.isInitialized) {
            mRobotAPI.motionPlay(motionId, true)
            Log.d("MainActivity", "Performing motion: $motionId")
        }
    }

    fun onVideoCallEnded() {
        isInVideoCall = false
        val targetType = if (isControlMode) FragmentType.CONTROL_MODE else FragmentType.MAIN
        showFragment(targetType)
    }

    private fun showFragment(type: FragmentType, extraData: Bundle? = null) {
        Log.d("MainActivity", "switching to $type")
        val fragment = when (type) {
            FragmentType.MAIN -> MainFragment()
            FragmentType.CONTROL_MODE -> ControlModeFragment()
            FragmentType.VIDEO_CALL -> {
                val peerId = extraData?.getString("peerId") ?: ""
                VideoCallFragment.newInstance(peerId, assignedRole, false)
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun registerWithId(robotId: String) {
        val message = JSONObject().apply {
            put("type", "register")
            put("id", robotId)
            put("role", "robot")
        }
        webSocket?.send(message.toString())
    }

    fun emergencyStop() {
        if (::mRobotAPI.isInitialized) {
            mRobotAPI.motionStop(true)
            mRobotAPI.stopTTS()
        }

        updateCurrentCommand("緊急停止 - 所有動作已停止")

        sendStatusToServer("emergency_stop", "all_actions_stopped", "ready")
    }

    // Fragment 更新方法
    private fun updateConnectionStatus(isConnected: Boolean) {
        runOnUiThread {
            val fragment = getCurrentFragment()
            if (fragment is MainFragment) {
                fragment.updateConnectionStatus(isConnected)
            }
        }
    }

    private fun updateRegistrationStatus(isRegistered: Boolean) {
        runOnUiThread {
            val fragment = getCurrentFragment()
            if (fragment is MainFragment) {
                fragment.updateRegistrationStatus(isRegistered)
            }
        }
    }

    private fun updateRoleStatus(role: String) {
        runOnUiThread {
        val fragment = getCurrentFragment()
            when (fragment) {
                is MainFragment -> fragment.updateRoleStatus(role)
                is ControlModeFragment -> fragment.updateRoleName(role)
            }
        }
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            val fragment = getCurrentFragment()
            when (fragment) {
                is MainFragment -> fragment.updateStatus(status)
            }
        }
    }

    private fun updateCurrentCommand(command: String) {
        runOnUiThread {
            val fragment = getCurrentFragment()
            when (fragment) {
                is MainFragment -> fragment.updateCurrentCommand(command)
            }
        }
    }

    private fun getCurrentFragment(): androidx.fragment.app.Fragment? {
        return supportFragmentManager.findFragmentById(R.id.fragment_container)
    }

    override fun onPause() {
        super.onPause()
        sendStatusToServer("robot_offline", "app_paused", "offline")
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            if (::mRobotAPI.isInitialized) {
                mRobotAPI.release()
            }
            Log.d("MainActivity", "NuwaRobotAPI released")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to release NuwaRobotAPI", e)
        }

        webSocket?.close(1000, "App closed")
    }
}