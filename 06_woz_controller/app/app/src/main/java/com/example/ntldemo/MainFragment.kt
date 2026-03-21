package com.example.ntldemo

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class MainFragment : Fragment() {

    private lateinit var etMyId: EditText
    private lateinit var etServerUrl: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvAssignedRole: TextView
    private lateinit var tvCurrentCommand: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)

        // 從 MainActivity 獲取狀態並更新 UI
        val mainActivity = activity as? MainActivity
        mainActivity?.let { activity ->
            updateUIFromActivity(activity)
        }
    }

    private fun initViews(view: View) {
        etMyId = view.findViewById(R.id.etMyId)
        etServerUrl = view.findViewById(R.id.etServerUrl)
        btnRegister = view.findViewById(R.id.btnRegister)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus)
        tvAssignedRole = view.findViewById(R.id.tvAssignedRole)
        tvCurrentCommand = view.findViewById(R.id.tvCurrentCommand)

        // 載入已儲存的 Server URL
        val prefs = requireContext().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        etServerUrl.setText(prefs.getString("server_url", MainActivity.DEFAULT_URL))

        // 當 URL 失去焦點時自動儲存並重新連線
        etServerUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newUrl = etServerUrl.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    prefs.edit().putString("server_url", newUrl).apply()
                    val mainActivity = activity as? MainActivity
                    mainActivity?.let {
                        it.websocketUrl = newUrl
                        it.initWebSocket()
                    }
                }
            }
        }
    }

    private fun updateUIFromActivity(activity: MainActivity) {
        etMyId.setText(activity.myId.ifEmpty { "Robot_${(1000..9999).random()}" })
        updateConnectionStatus(activity.isWebSocketConnected)
        updateRegistrationStatus(activity.isRegistered)
        updateRoleStatus(activity.assignedRole)
    }

    fun updateConnectionStatus(isConnected: Boolean) {
        tvConnectionStatus.text = if (isConnected) "已連接" else "未連接"
    }

    fun updateRegistrationStatus(isRegistered: Boolean) {
        btnRegister.text = if (isRegistered) "取消註冊" else "註冊機器人"
        etMyId.isEnabled = !isRegistered
    }

    fun updateRoleStatus(role: String) {
        tvAssignedRole.text = "角色: ${role.ifEmpty { "未分配" }}"
    }

    fun updateStatus(status: String) {
        tvStatus.text = status
    }

    fun updateCurrentCommand(command: String) {
        tvCurrentCommand.text = command
    }
}